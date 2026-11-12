package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.domain.Settlement
import com.hoppingmall.settlement.domain.SettlementSummary
import com.hoppingmall.settlement.domain.repository.SettlementSummaryRepository
import org.springframework.stereotype.Component

@Component
class SettlementSummarySynchronizer(
    private val settlementSummaryRepository: SettlementSummaryRepository
) {

    fun sync(settlement: Settlement) {
        val existing = settlementSummaryRepository.findBySettlementId(settlement.id!!)
        if (existing != null) {
            existing.updateFrom(settlement)
            return
        }
        if (settlementSummaryRepository.count() > 0) {
            settlementSummaryRepository.save(SettlementSummary.from(settlement))
        }
    }
}
