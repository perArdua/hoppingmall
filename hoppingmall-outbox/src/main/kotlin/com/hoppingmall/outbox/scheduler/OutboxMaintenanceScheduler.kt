package com.hoppingmall.outbox.scheduler

import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

@Service
class OutboxMaintenanceScheduler(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxEventPublisher: OutboxEventPublisher,
    transactionManager: PlatformTransactionManager
) {

    private val transactionTemplate = TransactionTemplate(transactionManager)

    private val logger = LoggerFactory.getLogger(OutboxMaintenanceScheduler::class.java)
    private val maxRetries = 3
    private val cleanupDays = 7L

    @Scheduled(cron = "\${outbox.maintenance.cleanup-cron:0 0 2 * * ?}")
    @Transactional
    fun cleanupProcessedEvents() {
        val cutoffDate = LocalDateTime.now().minusDays(cleanupDays)
        val deletedCount = outboxEventRepository.deleteProcessedEventsBefore(cutoffDate)

        if (deletedCount > 0) {
            logger.info("Cleaned up $deletedCount processed outbox events older than $cutoffDate")
        }
    }

    @Scheduled(fixedDelayString = "\${outbox.maintenance.stale-check-ms:300000}")
    fun handleStaleEvents() {
        val cutoffDate = LocalDateTime.now().minusMinutes(10)
        val staleEvents = transactionTemplate.execute {
            outboxEventRepository.findStaleEvents(cutoffDate, 50)
        } ?: return

        if (staleEvents.isEmpty()) return

        logger.warn("Found ${staleEvents.size} stale outbox events, retrying...")

        staleEvents.forEach { event ->
            val eventId = event.id ?: return@forEach
            if (event.retryCount >= maxRetries) {
                transactionTemplate.executeWithoutResult {
                    event.markAsFailedPermanently("Stale event - max retries exceeded")
                    outboxEventRepository.save(event)
                }
                return@forEach
            }
            val claimed = transactionTemplate.execute {
                outboxEventRepository.claimStaleEvent(
                    id = eventId,
                    nextStatus = OutboxStatus.RETRYING,
                    updatedAt = LocalDateTime.now(),
                    cutoffDate = cutoffDate,
                    maxRetries = maxRetries,
                    statuses = listOf(OutboxStatus.PENDING, OutboxStatus.FAILED, OutboxStatus.RETRYING)
                )
            } ?: 0
            if (claimed > 0) {
                outboxEventPublisher.publishEvent(eventId)
            }
        }
    }
}
