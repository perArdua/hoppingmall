package com.hoppingmall.outbox.scheduler

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Component
class OutboxPublishWaker(
    private val outboxEventPublisher: OutboxEventPublisher,
    @Value("\${outbox.publish.wake-on-commit:true}")
    private val enabled: Boolean
) {

    private val logger = LoggerFactory.getLogger(OutboxPublishWaker::class.java)
    private val queued = AtomicBoolean(false)
    private val executor: ExecutorService? = if (enabled) {
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "outbox-wake").apply { isDaemon = true } }
    } else {
        null
    }

    fun requestAfterCommit() {
        if (!enabled) return
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            wake()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = wake()
        })
    }

    fun wake() {
        val worker = executor ?: return
        if (!queued.compareAndSet(false, true)) return
        worker.execute {
            queued.set(false)
            try {
                outboxEventPublisher.publishPendingEvents()
            } catch (e: Exception) {
                logger.warn("Outbox wake-up publish failed, scheduled polling will retry: ${e.message}")
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        executor?.shutdownNow()
    }
}
