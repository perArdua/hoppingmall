package com.hoppingmall.dlq.archival

import com.hoppingmall.dlq.domain.DLQStatus
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
    @Transactional
    fun purgeArchivedMessages() {
        val retentionMillis = TimeUnit.DAYS.toMillis(dlqArchivalProperties.retentionDays)
        val cutoffTimestamp = System.currentTimeMillis() - retentionMillis

        val archivedFailedMessages = dlqMessageRepository.findArchivedMessagesBefore(
            DLQStatus.FAILED, cutoffTimestamp
        )

        if (archivedFailedMessages.isEmpty()) return

        dlqMessageRepository.deleteAll(archivedFailedMessages)
        log.info("아카이빙 완료된 FAILED DLQ 메시지 퍼지: {} 건 (보존기간 {} 일 초과)",
            archivedFailedMessages.size, dlqArchivalProperties.retentionDays)
    }
}
