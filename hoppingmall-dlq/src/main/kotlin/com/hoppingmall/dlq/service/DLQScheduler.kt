package com.hoppingmall.dlq.service

import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["dlq.scheduler.enabled"], havingValue = "true", matchIfMissing = true)
class DLQScheduler(
    private val dlqMessageRepository: DLQMessageRepository,
    private val dlqCommandService: DLQCommandService
) {

    private val logger = LoggerFactory.getLogger(DLQScheduler::class.java)

    companion object {
        private const val MAX_RETRY_COUNT = 3
        private const val AUTO_RETRY_BATCH_SIZE = 20
    }

    @Scheduled(fixedDelay = 30_000)
    fun autoRetryPendingMessages() {
        val now = System.currentTimeMillis()
        val pageable = PageRequest.of(0, AUTO_RETRY_BATCH_SIZE)
        val retryableMessages = dlqMessageRepository.findAutoRetryableMessages(
            DLQStatus.PENDING, MAX_RETRY_COUNT, now, pageable
        )

        if (retryableMessages.isEmpty) return

        logger.info("DLQ 자동 재처리 시작: {} 건", retryableMessages.content.size)

        var successCount = 0
        var failureCount = 0

        retryableMessages.content.forEach { message ->
            if (dlqCommandService.retryDLQMessage(message.id!!)) {
                successCount++
            } else {
                failureCount++
            }
        }

        logger.info("DLQ 자동 재처리 완료: 성공={}, 실패={}", successCount, failureCount)
    }
}
