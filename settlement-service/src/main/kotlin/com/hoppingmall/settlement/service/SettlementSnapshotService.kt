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
        var created = 0
        var updated = 0

        settlements.forEach { settlement ->
            val existing = settlementSummaryRepository.findBySettlementId(settlement.id!!)
            if (existing != null) {
                existing.updateFrom(settlement)
                updated++
            } else {
                settlementSummaryRepository.save(SettlementSummary.from(settlement))
                created++
            }
        }

        log.info("Settlement snapshot job completed: created=$created, updated=$updated")
    }
}
