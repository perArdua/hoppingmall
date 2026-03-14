package com.hoppingmall.mall.settlement.domain.repository

import com.hoppingmall.mall.settlement.domain.SettlementItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SettlementItemRepository : JpaRepository<SettlementItem, Long> {
    fun findBySettlementId(settlementId: Long): List<SettlementItem>
}
