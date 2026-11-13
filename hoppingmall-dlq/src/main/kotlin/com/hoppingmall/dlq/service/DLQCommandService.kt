package com.hoppingmall.dlq.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.dlq.archival.DLQArchivalService
import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.DLQMessageContentHash
import com.hoppingmall.dlq.domain.DeadLetterMessage
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import com.hoppingmall.dlq.metrics.DLQMetrics
import com.hoppingmall.dlq.publisher.DLQMessagePublisher
import com.hoppingmall.dlq.publisher.NoOpDLQMessagePublisher
import com.hoppingmall.dlq.reprocess.DLQReprocessors
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap

@Service
@Transactional
class DLQCommandService(
    private val dlqMessageRepository: DLQMessageRepository,
    private val dlqMessagePublisher: DLQMessagePublisher,
    private val dlqMetrics: DLQMetrics,
    private val dlqArchivalService: DLQArchivalService,
    private val avroEventConverter: AvroEventConverter? = null,
    private val dlqReprocessors: DLQReprocessors? = null
) {

    private val logger = LoggerFactory.getLogger(DLQCommandService::class.java)

    private val processingMessages = ConcurrentHashMap<String, Boolean>()

    companion object {
        const val MAX_RETRY_COUNT = 3
        private const val EXHAUSTED_BATCH_SIZE = 100
        private const val EVENT_TYPE_FIELD = "eventType"
        private const val NO_PUBLISHER_NOTE = "재발행 수단 없음 (Kafka 재발행기 미설정)"
        private val objectMapper = ObjectMapper()
    }

    fun saveDLQMessage(deadLetterMessage: DeadLetterMessage) {
        try {
            val consumerGroup = deadLetterMessage.consumerGroup
            val exists = if (consumerGroup == null) {
                dlqMessageRepository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
                    deadLetterMessage.originalTopic,
                    deadLetterMessage.originalPartition,
                    deadLetterMessage.originalOffset
                )
            } else {
                dlqMessageRepository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffsetAndConsumerGroup(
                    deadLetterMessage.originalTopic,
                    deadLetterMessage.originalPartition,
                    deadLetterMessage.originalOffset,
                    consumerGroup
                )
            }

            if (exists) {
                logger.warn("중복 DLQ 메시지 저장 시도: topic={}, partition={}, offset={}",
                    deadLetterMessage.originalTopic, deadLetterMessage.originalPartition, deadLetterMessage.originalOffset)
                return
            }

            val contentHash = DLQMessageContentHash.of(
                deadLetterMessage.originalTopic,
                deadLetterMessage.originalKey,
                deadLetterMessage.originalValue
            )
            val sameContent = if (consumerGroup == null) {
                dlqMessageRepository.findFirstByOriginalTopicAndContentHashOrderByIdDesc(
                    deadLetterMessage.originalTopic, contentHash
                )
            } else {
                dlqMessageRepository.findFirstByOriginalTopicAndConsumerGroupAndContentHashOrderByIdDesc(
                    deadLetterMessage.originalTopic, consumerGroup, contentHash
                )
            }
            if (sameContent != null) {
                handleRepeatedFailure(sameContent, deadLetterMessage)
                return
            }

            val dlqMessage = DLQMessage(
                originalTopic = deadLetterMessage.originalTopic,
                originalPartition = deadLetterMessage.originalPartition,
                originalOffset = deadLetterMessage.originalOffset,
                originalKey = deadLetterMessage.originalKey,
                originalValue = deadLetterMessage.originalValue,
                exceptionMessage = deadLetterMessage.exception,
                errorTimestamp = deadLetterMessage.timestamp,
                contentHash = contentHash,
                consumerGroup = consumerGroup
            )

            if (dlqMessage.isNonRetryableException()) {
                dlqMessage.markAsFailed("비재시도 에러: ${deadLetterMessage.exception}")
                dlqMessageRepository.save(dlqMessage)
                logger.info("비재시도 에러로 즉시 FAILED 처리: topic={}, error={}",
                    deadLetterMessage.originalTopic, deadLetterMessage.exception)
                return
            }

            if (dlqMessagePublisher is NoOpDLQMessagePublisher && !canReprocessDirectly(dlqMessage)) {
                dlqMessage.markAsFailed(NO_PUBLISHER_NOTE)
                dlqMessageRepository.save(dlqMessage)
                dlqMetrics.recordDlqSaved(deadLetterMessage.originalTopic)
                logger.warn("재발행 수단이 없어 DLQ 메시지를 FAILED로 저장: topic={}, offset={}",
                    deadLetterMessage.originalTopic, deadLetterMessage.originalOffset)
                return
            }

            dlqMessage.nextRetryAt = DLQMessage.calculateNextRetryAt(0)
            dlqMessageRepository.save(dlqMessage)
            dlqMetrics.recordDlqSaved(deadLetterMessage.originalTopic)
            logger.info("DLQ 메시지 저장 완료: topic={}, partition={}, offset={}",
                deadLetterMessage.originalTopic, deadLetterMessage.originalPartition, deadLetterMessage.originalOffset)

        } catch (e: Exception) {
            logger.error("DLQ 메시지 저장 실패: topic={}, error={}", deadLetterMessage.originalTopic, e.message, e)
            throw e
        }
    }

    fun retryDLQMessage(dlqMessageId: Long): Boolean {
        val messageKey = "dlq:$dlqMessageId"

        if (processingMessages.putIfAbsent(messageKey, true) != null) {
            logger.warn("이미 처리 중인 DLQ 메시지: id={}", dlqMessageId)
            return false
        }

        return try {
            val dlqMessage = dlqMessageRepository.findByIdOrNull(dlqMessageId)
            if (dlqMessage == null) {
                logger.warn("DLQ 메시지를 찾을 수 없음: id={}", dlqMessageId)
                return false
            }

            if (dlqMessage.status != DLQStatus.PENDING) {
                logger.warn("재처리할 수 없는 상태의 DLQ 메시지: id={}, status={}", dlqMessageId, dlqMessage.status)
                return false
            }

            if (canReprocessDirectly(dlqMessage)) {
                return reprocessDirectly(dlqMessage)
            }

            if (dlqMessagePublisher is NoOpDLQMessagePublisher) {
                dlqMessage.markAsFailed(NO_PUBLISHER_NOTE)
                dlqMessageRepository.save(dlqMessage)
                logger.warn("재발행 수단이 없어 DLQ 메시지를 FAILED로 전환: id={}", dlqMessageId)
                return false
            }

            if (dlqMessage.retryCount >= MAX_RETRY_COUNT) {
                dlqMessage.markAsFailed("최대 재시도 횟수 초과")
                dlqMessageRepository.save(dlqMessage)
                logger.warn("최대 재시도 횟수 초과: id={}, retryCount={}", dlqMessageId, dlqMessage.retryCount)
                return false
            }

            val originalValue = try {
                reconstructOriginalMessage(dlqMessage)
            } catch (e: UnrepublishableMessageException) {
                dlqMessage.markAsFailed("재발행 불가: ${e.message}")
                dlqMessageRepository.save(dlqMessage)
                dlqMetrics.recordDlqRetryFailed()
                logger.warn("DLQ 메시지 재발행 불가로 FAILED 처리: id={}, reason={}", dlqMessageId, e.message)
                return false
            }

            val claimed = dlqMessageRepository.claimForRetry(
                dlqMessageId, DLQStatus.PENDING, dlqMessage.retryCount, DLQStatus.RETRYING
            )
            if (claimed == 0) {
                logger.info("다른 인스턴스가 먼저 집은 DLQ 메시지: id={}", dlqMessageId)
                return false
            }

            logger.info("DLQ 메시지 재처리 시작: id={}, topic={}", dlqMessageId, dlqMessage.originalTopic)

            dlqMessage.incrementRetry()
            dlqMessageRepository.save(dlqMessage)

            val published = dlqMessagePublisher.publish(
                dlqMessage.originalTopic,
                dlqMessage.originalKey ?: "",
                originalValue
            )

            if (published) {
                dlqMessage.markAsProcessed("재처리 성공")
                dlqMessageRepository.save(dlqMessage)
                dlqMetrics.recordDlqRetrySuccess()
                logger.info("DLQ 메시지 재처리 성공: id={}, topic={}", dlqMessageId, dlqMessage.originalTopic)
            } else {
                dlqMetrics.recordDlqRetryFailed()
                if (dlqMessage.retryCount >= MAX_RETRY_COUNT) {
                    dlqMessage.markAsFailed("최대 재시도 후 재발행 실패")
                } else {
                    dlqMessage.scheduleNextRetry()
                }
                dlqMessageRepository.save(dlqMessage)
                logger.warn("DLQ 메시지 재발행 실패: id={}, retryCount={}, status={}",
                    dlqMessageId, dlqMessage.retryCount, dlqMessage.status)
            }

            published

        } catch (e: Exception) {
            logger.error("DLQ 메시지 재처리 실패: id={}, error={}", dlqMessageId, e.message, e)
            dlqMetrics.recordDlqRetryFailed()

            try {
                val dlqMessage = dlqMessageRepository.findByIdOrNull(dlqMessageId)
                dlqMessage?.let {
                    if (it.retryCount >= MAX_RETRY_COUNT) {
                        it.markAsFailed("최대 재시도 후 실패: ${e.message}")
                    } else {
                        it.scheduleNextRetry()
                    }
                    dlqMessageRepository.save(it)
                }
            } catch (updateException: Exception) {
                logger.error("DLQ 메시지 상태 업데이트 실패: id={}", dlqMessageId, updateException)
            }

            false
        } finally {
            processingMessages.remove(messageKey)
        }
    }

    fun failExhaustedMessages(): Int {
        val exhausted = dlqMessageRepository.findByStatusAndRetryCountGreaterThanEqual(
            DLQStatus.PENDING, MAX_RETRY_COUNT, PageRequest.of(0, EXHAUSTED_BATCH_SIZE)
        )
        if (exhausted.isEmpty()) return 0

        exhausted.forEach { it.markAsFailed("최대 재시도 횟수 초과") }
        dlqMessageRepository.saveAll(exhausted)
        logger.warn("재시도를 다 쓴 DLQ 메시지를 FAILED로 전환: {} 건", exhausted.size)
        return exhausted.size
    }

    fun retryDLQMessagesByTopic(topic: String, maxCount: Int = 50): Map<String, Any> {
        val pageable = PageRequest.of(0, maxCount)
        val pendingMessages = dlqMessageRepository.findByOriginalTopicAndStatusOrderByCreatedAtDesc(
            topic, DLQStatus.PENDING, pageable
        )

        var successCount = 0
        var failureCount = 0
        val errors = mutableListOf<String>()

        pendingMessages.content.forEach { message ->
            if (retryDLQMessage(message.id!!)) {
                successCount++
            } else {
                failureCount++
                errors.add("ID ${message.id}: 재처리 실패")
            }
        }

        return mapOf(
            "topic" to topic,
            "totalAttempted" to pendingMessages.content.size,
            "successCount" to successCount,
            "failureCount" to failureCount,
            "errors" to errors.take(10)
        )
    }

    fun clearProcessedDLQMessages(topic: String): Long {
        val processedMessages = dlqMessageRepository.findByOriginalTopicAndStatusOrderByCreatedAtDesc(
            topic, DLQStatus.PROCESSED, PageRequest.of(0, 1000)
        )

        val deleteCount = processedMessages.content.size.toLong()
        if (deleteCount > 0) {
            val archivedCount = dlqArchivalService.archiveBatch(processedMessages.content)
            if (archivedCount < processedMessages.content.size) {
                logger.warn("일부 DLQ 메시지 아카이빙 실패: topic={}, 아카이빙={}/{}", topic, archivedCount, deleteCount)
            }
            dlqMessageRepository.deleteAll(processedMessages.content)
            logger.info("처리 완료된 DLQ 메시지 삭제: topic={}, count={}, archived={}", topic, deleteCount, archivedCount)
        }

        return deleteCount
    }

    fun reconstructOriginalMessage(dlqMessage: DLQMessage): Any? {
        val value = dlqMessage.originalValue ?: return null
        val converter = avroEventConverter ?: return value
        val eventType = readEventType(value)
            ?: throw UnrepublishableMessageException("$EVENT_TYPE_FIELD 없음")
        return try {
            converter.convertJsonToAvro(eventType, value)
        } catch (e: Exception) {
            throw UnrepublishableMessageException("Avro 변환 실패 ($EVENT_TYPE_FIELD=$eventType): ${e.message}")
        }
    }

    private fun canReprocessDirectly(dlqMessage: DLQMessage): Boolean =
        dlqReprocessors?.supports(dlqMessage.originalTopic, dlqMessage.consumerGroup) == true

    private fun reprocessDirectly(dlqMessage: DLQMessage): Boolean {
        val dlqMessageId = dlqMessage.id!!
        val consumerGroup = dlqMessage.consumerGroup!!

        if (dlqMessage.retryCount >= MAX_RETRY_COUNT) {
            dlqMessage.markAsFailed("최대 재시도 횟수 초과")
            dlqMessageRepository.save(dlqMessage)
            logger.warn("최대 재시도 횟수 초과: id={}, retryCount={}", dlqMessageId, dlqMessage.retryCount)
            return false
        }

        val claimed = dlqMessageRepository.claimForRetry(
            dlqMessageId, DLQStatus.PENDING, dlqMessage.retryCount, DLQStatus.RETRYING
        )
        if (claimed == 0) {
            logger.info("다른 인스턴스가 먼저 집은 DLQ 메시지: id={}", dlqMessageId)
            return false
        }

        logger.info("DLQ 메시지 직접 재처리 시작: id={}, topic={}, group={}",
            dlqMessageId, dlqMessage.originalTopic, consumerGroup)

        dlqMessage.incrementRetry()
        dlqMessageRepository.save(dlqMessage)

        return try {
            dlqReprocessors!!.reprocess(
                dlqMessage.originalTopic, consumerGroup, dlqMessage.originalKey, dlqMessage.originalValue
            )
            dlqMessage.markAsProcessed("직접 재처리 성공")
            dlqMessageRepository.save(dlqMessage)
            dlqMetrics.recordDlqRetrySuccess()
            logger.info("DLQ 메시지 직접 재처리 성공: id={}, topic={}, group={}",
                dlqMessageId, dlqMessage.originalTopic, consumerGroup)
            true
        } catch (e: Exception) {
            dlqMessage.exceptionMessage = "${e.javaClass.name}: ${e.message}"
            dlqMessage.errorTimestamp = System.currentTimeMillis()
            when {
                dlqMessage.isNonRetryableException() -> dlqMessage.markAsFailed("직접 재처리 중 비재시도 에러")
                dlqMessage.retryCount >= MAX_RETRY_COUNT -> dlqMessage.markAsFailed("최대 재시도 후 직접 재처리 실패")
                else -> dlqMessage.scheduleNextRetry()
            }
            dlqMessageRepository.save(dlqMessage)
            dlqMetrics.recordDlqRetryFailed()
            logger.warn("DLQ 메시지 직접 재처리 실패: id={}, retryCount={}, status={}, error={}",
                dlqMessageId, dlqMessage.retryCount, dlqMessage.status, e.message)
            false
        }
    }

    private fun handleRepeatedFailure(existing: DLQMessage, deadLetterMessage: DeadLetterMessage) {
        if (existing.status != DLQStatus.PROCESSED) {
            logger.warn("같은 내용의 DLQ 메시지가 이미 있음: id={}, status={}, topic={}, offset={}",
                existing.id, existing.status, deadLetterMessage.originalTopic, deadLetterMessage.originalOffset)
            return
        }

        existing.reopenAfterRepublishFailure(deadLetterMessage.exception, deadLetterMessage.timestamp)
        if (existing.retryCount >= MAX_RETRY_COUNT) {
            existing.markAsFailed("재발행 뒤 다시 실패, 최대 재시도 횟수 초과")
        } else if (existing.isNonRetryableException()) {
            existing.markAsFailed("재발행 뒤 비재시도 에러: ${deadLetterMessage.exception}")
        }
        dlqMessageRepository.save(existing)
        dlqMetrics.recordDlqSaved(deadLetterMessage.originalTopic)
        logger.warn("재발행한 DLQ 메시지가 다시 실패: id={}, retryCount={}, status={}",
            existing.id, existing.retryCount, existing.status)
    }

    private fun readEventType(value: String): String? {
        val node = runCatching { objectMapper.readTree(value) }.getOrNull() ?: return null
        if (!node.isObject) return null
        val eventType = node.path(EVENT_TYPE_FIELD)
        return if (eventType.isTextual && eventType.asText().isNotBlank()) eventType.asText() else null
    }
}

private class UnrepublishableMessageException(message: String) : RuntimeException(message)
