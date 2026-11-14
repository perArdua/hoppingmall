package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.domain.SettlementSummary
import com.hoppingmall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.settlement.domain.repository.SettlementSummaryRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Limit
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SettlementSnapshotService(
    private val settlementRepository: SettlementRepository,
    private val settlementSummaryRepository: SettlementSummaryRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val BATCH_SIZE = 200
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun createSnapshot() {
        log.info("Settlement snapshot job started")

        var totalCreated = 0
        var totalUpdated = 0
        var lastId = 0L

        while (true) {
            val settlements = settlementRepository.findByIdGreaterThanOrderByIdAsc(lastId, Limit.of(BATCH_SIZE))

            if (settlements.isEmpty()) break

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

            totalCreated += newSummaries.size
            totalUpdated += settlements.size - newSummaries.size
            lastId = settlements.last().id!!

            if (settlements.size < BATCH_SIZE) break
        }

        log.info("Settlement snapshot job completed: created=$totalCreated, updated=$totalUpdated")
    }
}
