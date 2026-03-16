package com.hoppingmall.settlement.domain.repository

import com.hoppingmall.settlement.domain.SettlementItem
import org.springframework.data.jpa.repository.JpaRepository

interface SettlementItemRepository : JpaRepository<SettlementItem, Long> {
    fun findBySettlementId(settlementId: Long): List<SettlementItem>
}
