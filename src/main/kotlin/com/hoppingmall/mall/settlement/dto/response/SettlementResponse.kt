package com.hoppingmall.mall.settlement.dto.response

import com.hoppingmall.mall.settlement.domain.Settlement
import com.hoppingmall.mall.settlement.enum.SettlementStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class SettlementResponse(
    val id: Long,
    val sellerId: Long,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val totalSalesAmount: BigDecimal,
    val totalRefundAmount: BigDecimal,
    val commissionRate: BigDecimal,
    val commissionAmount: BigDecimal,
    val settlementAmount: BigDecimal,
    val status: SettlementStatus,
    val confirmedAt: LocalDateTime?,
    val paidAt: LocalDateTime?,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(settlement: Settlement): SettlementResponse {
            return SettlementResponse(
                id = settlement.id!!,
                sellerId = settlement.sellerId,
                periodStart = settlement.periodStart,
                periodEnd = settlement.periodEnd,
                totalSalesAmount = settlement.totalSalesAmount,
                totalRefundAmount = settlement.totalRefundAmount,
                commissionRate = settlement.commissionRate,
                commissionAmount = settlement.commissionAmount,
                settlementAmount = settlement.settlementAmount,
                status = settlement.status,
                confirmedAt = settlement.confirmedAt,
                paidAt = settlement.paidAt,
                createdAt = settlement.createdAt
            )
        }
    }
}
