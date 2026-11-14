package com.hoppingmall.dlq.archival

import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.repository.DLQ_PURGE_BATCH_SIZE
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(name = ["dlq.archival.enabled"], havingValue = "true")
class DLQArchivalScheduler(
    private val dlqMessageRepository: DLQMessageRepository,
    private val dlqArchivalService: DLQArchivalService,
    private val dlqArchivalProperties: DLQArchivalProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    fun archiveFailedMessages() {
        val pageable = PageRequest.of(0, dlqArchivalProperties.batchSize)
        val unarchivedMessages = dlqMessageRepository.findUnarchivedByStatus(DLQStatus.FAILED, pageable)

        if (unarchivedMessages.isEmpty) return

        log.info("DLQ FAILED 메시지 아카이빙 시작: {} 건", unarchivedMessages.content.size)

        var successCount = 0
        unarchivedMessages.content.forEach { message ->
            if (dlqArchivalService.archive(message)) {
                message.markAsArchived()
                dlqMessageRepository.save(message)
                successCount++
            }
        }

        log.info("DLQ FAILED 메시지 아카이빙 완료: 성공={}/{}", successCount, unarchivedMessages.content.size)
    }

    @Scheduled(fixedDelay = 3_600_000)
    fun purgeArchivedMessages() {
        val retentionMillis = TimeUnit.DAYS.toMillis(dlqArchivalProperties.retentionDays)
        val cutoffTimestamp = System.currentTimeMillis() - retentionMillis

        var purgedCount = 0
        var batches = 0
        do {
            val deleted = dlqMessageRepository.deleteArchivedMessagesBefore(DLQStatus.FAILED.name, cutoffTimestamp)
            purgedCount += deleted
            batches++
        } while (deleted == DLQ_PURGE_BATCH_SIZE && batches < dlqArchivalProperties.purgeMaxBatches)

        if (purgedCount == 0) return

        log.info("아카이빙 완료된 FAILED DLQ 메시지 퍼지: {} 건, {} 회 (보존기간 {} 일 초과)",
            purgedCount, batches, dlqArchivalProperties.retentionDays)
    }
}
