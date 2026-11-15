package com.hoppingmall.settlement.domain.repository

import com.hoppingmall.settlement.domain.SettlementCarryOver
import org.springframework.data.jpa.repository.JpaRepository

interface SettlementCarryOverRepository : JpaRepository<SettlementCarryOver, Long> {
    fun findBySellerIdAndAppliedSettlementIdIsNullOrderByIdAsc(sellerId: Long): List<SettlementCarryOver>
}
