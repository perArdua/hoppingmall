package com.hoppingmall.dlq.service

import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.DeadLetterMessage
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import com.hoppingmall.dlq.metrics.DLQMetrics
import com.hoppingmall.dlq.publisher.DLQMessagePublisher
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap

@Service
@Transactional
class DLQCommandService(
    private val dlqMessageRepository: DLQMessageRepository,
    private val dlqMessagePublisher: DLQMessagePublisher,
    private val dlqMetrics: DLQMetrics
) {

    private val logger = LoggerFactory.getLogger(DLQCommandService::class.java)

    private val processingMessages = ConcurrentHashMap<String, Boolean>()

    companion object {
        private const val MAX_RETRY_COUNT = 3
    }

    fun saveDLQMessage(deadLetterMessage: DeadLetterMessage) {
        try {
            val exists = dlqMessageRepository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
                deadLetterMessage.originalTopic,
                deadLetterMessage.originalPartition,
                deadLetterMessage.originalOffset
            )

            if (exists) {
                logger.warn("중복 DLQ 메시지 저장 시도: topic={}, partition={}, offset={}",
                    deadLetterMessage.originalTopic, deadLetterMessage.originalPartition, deadLetterMessage.originalOffset)
                return
            }

            val dlqMessage = DLQMessage(
                originalTopic = deadLetterMessage.originalTopic,
                originalPartition = deadLetterMessage.originalPartition,
                originalOffset = deadLetterMessage.originalOffset,
                originalKey = deadLetterMessage.originalKey,
                originalValue = deadLetterMessage.originalValue,
                exceptionMessage = deadLetterMessage.exception,
                errorTimestamp = deadLetterMessage.timestamp
            )

            if (dlqMessage.isNonRetryableException()) {
                dlqMessage.markAsFailed("비재시도 에러: ${deadLetterMessage.exception}")
                dlqMessageRepository.save(dlqMessage)
                logger.info("비재시도 에러로 즉시 FAILED 처리: topic={}, error={}",
                    deadLetterMessage.originalTopic, deadLetterMessage.exception)
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
            val dlqMessage = dlqMessageRepository.findById(dlqMessageId).orElse(null)
            if (dlqMessage == null) {
                logger.warn("DLQ 메시지를 찾을 수 없음: id={}", dlqMessageId)
                return false
            }

            if (dlqMessage.status != DLQStatus.PENDING) {
                logger.warn("재처리할 수 없는 상태의 DLQ 메시지: id={}, status={}", dlqMessageId, dlqMessage.status)
                return false
            }

            if (dlqMessage.retryCount >= MAX_RETRY_COUNT) {
                dlqMessage.markAsFailed("최대 재시도 횟수 초과")
                dlqMessageRepository.save(dlqMessage)
                logger.warn("최대 재시도 횟수 초과: id={}, retryCount={}", dlqMessageId, dlqMessage.retryCount)
                return false
            }

            logger.info("DLQ 메시지 재처리 시작: id={}, topic={}", dlqMessageId, dlqMessage.originalTopic)

            dlqMessage.incrementRetry()
            dlqMessageRepository.save(dlqMessage)

            val originalValue = reconstructOriginalMessage(dlqMessage)
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
                dlqMessage.scheduleNextRetry()
                dlqMessageRepository.save(dlqMessage)
                logger.warn("DLQ 메시지 재발행 불가 (Publisher 미설정): id={}", dlqMessageId)
            }

            published

        } catch (e: Exception) {
            logger.error("DLQ 메시지 재처리 실패: id={}, error={}", dlqMessageId, e.message, e)
            dlqMetrics.recordDlqRetryFailed()

            try {
                val dlqMessage = dlqMessageRepository.findById(dlqMessageId).orElse(null)
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
            dlqMessageRepository.deleteAll(processedMessages.content)
            logger.info("처리 완료된 DLQ 메시지 삭제: topic={}, count={}", topic, deleteCount)
        }

        return deleteCount
    }

    fun reconstructOriginalMessage(dlqMessage: DLQMessage): Any? {
        return try {
            dlqMessage.originalValue?.let { value ->
                when {
                    value.startsWith("{") && value.endsWith("}") -> value
                    value.startsWith("[") && value.endsWith("]") -> value
                    else -> value
                }
            }
        } catch (e: Exception) {
            logger.warn("원본 메시지 복원 실패, 문자열로 처리: id={}, error={}", dlqMessage.id, e.message)
            dlqMessage.originalValue
        }
    }
}
