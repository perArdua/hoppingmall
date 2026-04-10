package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.domain.SettlementSummary
import com.hoppingmall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.settlement.domain.repository.SettlementSummaryRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SettlementSnapshotService(
    private val settlementRepository: SettlementRepository,
    private val settlementSummaryRepository: SettlementSummaryRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun createSnapshot() {
        log.info("Settlement snapshot job started")

        val settlements = settlementRepository.findAll()
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

        if (newSummaries.isNotEmpty()) {
            settlementSummaryRepository.saveAll(newSummaries)
        }

        val created = newSummaries.size
        val updated = settlements.size - created

        log.info("Settlement snapshot job completed: created=$created, updated=$updated")
    }
}
