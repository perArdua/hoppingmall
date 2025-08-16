package com.hoppingmall.mall.global.common.service

import com.hoppingmall.mall.global.common.config.DeadLetterMessage
import com.hoppingmall.mall.global.common.domain.DLQMessage
import com.hoppingmall.mall.global.common.domain.DLQStatus
import com.hoppingmall.mall.global.common.domain.repository.DLQMessageRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap

@Service
@Transactional
class DLQService(
    private val dlqMessageRepository: DLQMessageRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    
    private val logger = LoggerFactory.getLogger(DLQService::class.java)
    
    // 동시성 처리를 위한 메시지 처리 중 상태 추적
    private val processingMessages = ConcurrentHashMap<String, Boolean>()
    
    /**
     * DLQ 메시지 저장
     */
    fun saveDLQMessage(deadLetterMessage: DeadLetterMessage) {
        try {
            // 중복 메시지 체크
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
            
            dlqMessageRepository.save(dlqMessage)
            logger.info("DLQ 메시지 저장 완료: topic={}, partition={}, offset={}", 
                deadLetterMessage.originalTopic, deadLetterMessage.originalPartition, deadLetterMessage.originalOffset)
                
        } catch (e: Exception) {
            logger.error("DLQ 메시지 저장 실패: topic={}, error={}", deadLetterMessage.originalTopic, e.message, e)
            throw e
        }
    }
    
    /**
     * 토픽별 DLQ 메시지 조회
     */
    @Transactional(readOnly = true)
    fun getDLQMessages(topic: String, page: Int = 0, size: Int = 100): Page<DLQMessage> {
        val pageable = PageRequest.of(page, size)
        return dlqMessageRepository.findByOriginalTopicOrderByCreatedAtDesc(topic, pageable)
    }
    
    /**
     * 상태별 DLQ 메시지 조회
     */
    @Transactional(readOnly = true)
    fun getDLQMessagesByStatus(status: DLQStatus, page: Int = 0, size: Int = 100): Page<DLQMessage> {
        val pageable = PageRequest.of(page, size)
        return dlqMessageRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
    }
    
    /**
     * DLQ 통계 조회
     */
    @Transactional(readOnly = true)
    fun getDLQStats(): Map<String, Any> {
        val totalCount = dlqMessageRepository.count()
        val pendingCount = dlqMessageRepository.countByStatus(DLQStatus.PENDING)
        val processedCount = dlqMessageRepository.countByStatus(DLQStatus.PROCESSED)
        val failedCount = dlqMessageRepository.countByStatus(DLQStatus.FAILED)
        
        val topicStats = dlqMessageRepository.getDLQStatsByTopic()
        
        return mapOf(
            "totalMessages" to totalCount,
            "pendingCount" to pendingCount,
            "processedCount" to processedCount,
            "failedCount" to failedCount,
            "topicStats" to topicStats.map { stats ->
                mapOf(
                    "topic" to stats.topic,
                    "total" to stats.totalCount,
                    "pending" to stats.pendingCount,
                    "processed" to stats.processedCount,
                    "failed" to stats.failedCount
                )
            },
            "lastUpdated" to System.currentTimeMillis()
        )
    }
    
    /**
     * DLQ 메시지 재처리
     */
    fun retryDLQMessage(dlqMessageId: Long): Boolean {
        val messageKey = "dlq:$dlqMessageId"
        
        // 중복 처리 방지
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
            
            // 재시도 횟수 체크
            if (dlqMessage.retryCount >= 3) {
                dlqMessage.markAsFailed("최대 재시도 횟수 초과")
                dlqMessageRepository.save(dlqMessage)
                logger.warn("최대 재시도 횟수 초과: id={}, retryCount={}", dlqMessageId, dlqMessage.retryCount)
                return false
            }
            
            logger.info("DLQ 메시지 재처리 시작: id={}, topic={}", dlqMessageId, dlqMessage.originalTopic)
            
            // 재시도 카운트 증가
            dlqMessage.incrementRetry()
            dlqMessageRepository.save(dlqMessage)
            
            // 원본 토픽으로 메시지 재전송
            val originalValue = reconstructOriginalMessage(dlqMessage)
            kafkaTemplate.send(dlqMessage.originalTopic, dlqMessage.originalKey, originalValue)
            
            // 처리 완료 처리
            dlqMessage.markAsProcessed("수동 재처리 성공")
            dlqMessageRepository.save(dlqMessage)
            
            logger.info("DLQ 메시지 재처리 성공: id={}, topic={}", dlqMessageId, dlqMessage.originalTopic)
            true
            
        } catch (e: Exception) {
            logger.error("DLQ 메시지 재처리 실패: id={}, error={}", dlqMessageId, e.message, e)
            
            // 실패 시 상태 업데이트
            try {
                val dlqMessage = dlqMessageRepository.findById(dlqMessageId).orElse(null)
                dlqMessage?.let {
                    it.markAsFailed("재처리 실패: ${e.message}")
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
    
    /**
     * 토픽별 DLQ 메시지 일괄 재처리
     */
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
            "errors" to errors.take(10) // 최대 10개 에러만 표시
        )
    }
    
    /**
     * 토픽별 DLQ 메시지 삭제 (PROCESSED 상태만)
     */
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
    
    private fun reconstructOriginalMessage(dlqMessage: DLQMessage): Any? {
        return try {
            dlqMessage.originalValue?.let { value ->
                when {
                    value.startsWith("{") && value.endsWith("}") -> value // JSON 객체
                    value.startsWith("[") && value.endsWith("]") -> value // JSON 배열
                    else -> value // 일반 문자열
                }
            }
        } catch (e: Exception) {
            logger.warn("원본 메시지 복원 실패, 문자열로 처리: id={}, error={}", dlqMessage.id, e.message)
            dlqMessage.originalValue
        }
    }
}