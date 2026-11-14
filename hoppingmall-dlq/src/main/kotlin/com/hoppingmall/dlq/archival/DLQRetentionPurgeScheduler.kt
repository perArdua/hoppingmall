package com.hoppingmall.dlq.archival

import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.repository.DLQ_PURGE_BATCH_SIZE
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(name = ["dlq.archival.enabled"], havingValue = "false", matchIfMissing = true)
@EnableConfigurationProperties(DLQRetentionProperties::class)
class DLQRetentionPurgeScheduler(
    private val dlqMessageRepository: DLQMessageRepository,
    private val dlqRetentionProperties: DLQRetentionProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 3_600_000)
    fun purgeFinishedMessages() {
        val cutoffTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(dlqRetentionProperties.days)

        var purgedCount = 0
        var batches = 0
        do {
            val deleted = dlqMessageRepository.deleteFinishedMessagesBefore(FINISHED_STATUSES, cutoffTimestamp)
            purgedCount += deleted
            batches++
        } while (deleted == DLQ_PURGE_BATCH_SIZE && batches < dlqRetentionProperties.purgeMaxBatches)

        if (purgedCount == 0) return

        log.info("처리 끝난 DLQ 메시지 퍼지(보관 없음): {} 건, {} 회 (보존기간 {} 일 초과)",
            purgedCount, batches, dlqRetentionProperties.days)
    }

    companion object {
        private val FINISHED_STATUSES = listOf(DLQStatus.PROCESSED.name, DLQStatus.FAILED.name)
    }
}
