package com.hoppingmall.settlement.domain.repository

import com.hoppingmall.settlement.domain.SettlementRefund
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SettlementRefundRepository : JpaRepository<SettlementRefund, Long> {
    fun findBySettlementId(settlementId: Long): List<SettlementRefund>

    @Query("SELECT r.refundId FROM SettlementRefund r WHERE r.refundId IN :refundIds")
    fun findRefundIdsIn(@Param("refundIds") refundIds: Collection<Long>): List<Long>
}
