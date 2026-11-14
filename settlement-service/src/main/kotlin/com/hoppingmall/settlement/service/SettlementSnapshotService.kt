package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.domain.SettlementSummary
import com.hoppingmall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.settlement.domain.repository.SettlementSummaryRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Limit
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class SettlementSnapshotService(
    private val settlementRepository: SettlementRepository,
    private val settlementSummaryRepository: SettlementSummaryRepository,
    transactionManager: PlatformTransactionManager
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val transactionTemplate = TransactionTemplate(transactionManager)

    companion object {
        private const val BATCH_SIZE = 200
    }

    @Scheduled(cron = "\${settlement.snapshot.cron:0 0 3 * * *}")
    fun createSnapshot() {
        log.info("Settlement snapshot job started")

        var totalCreated = 0
        var totalUpdated = 0
        var lastId = 0L

        while (true) {
            val page = transactionTemplate.execute { snapshotPage(lastId) }!!

            totalCreated += page.created
            totalUpdated += page.updated
            lastId = page.lastId ?: break

            if (page.size < BATCH_SIZE) break
        }

        log.info("Settlement snapshot job completed: created=$totalCreated, updated=$totalUpdated")
    }

    private fun snapshotPage(afterId: Long): PageResult {
        val settlements = settlementRepository.findWithShareLockByIdGreaterThanOrderByIdAsc(afterId, Limit.of(BATCH_SIZE))

        if (settlements.isEmpty()) return PageResult(size = 0, created = 0, updated = 0, lastId = null)

        val summaryMap = settlementSummaryRepository
            .findBySettlementIdIn(settlements.map { it.id!! })
            .associateBy { it.settlementId }

        val newSummaries = mutableListOf<SettlementSummary>()

        settlements.forEach { settlement ->
            val existing = summaryMap[settlement.id!!]
            if (existing != null) {
                existing.updateFrom(settlement)
            } else {
                newSummaries.add(SettlementSummary.from(settlement))
            }
        }

        newSummaries.forEach { settlementSummaryRepository.insertIfAbsent(it) }

        return PageResult(
            size = settlements.size,
            created = newSummaries.size,
            updated = settlements.size - newSummaries.size,
            lastId = settlements.last().id!!
        )
    }

    private class PageResult(
        val size: Int,
        val created: Int,
        val updated: Int,
        val lastId: Long?
    )
}
