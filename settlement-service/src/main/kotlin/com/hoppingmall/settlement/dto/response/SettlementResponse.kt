package com.hoppingmall.settlement.dto.response

import com.hoppingmall.settlement.domain.Settlement
import com.hoppingmall.settlement.enums.SettlementStatus
import io.swagger.v3.oas.annotations.media.Schema
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
    val createdAt: LocalDateTime,
    @field:Schema(description = "앞 정산에서 이월되어 이번 지급액에서 뺀 금액. 0 이상.")
    val carriedInAmount: BigDecimal = BigDecimal.ZERO,
    @field:Schema(description = "이번 정산에서 지급액(0 바닥)으로 다 빼지 못해 다음 정산으로 넘긴 금액. 0 이상.")
    val carryOverAmount: BigDecimal = BigDecimal.ZERO
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
                createdAt = settlement.createdAt,
                carriedInAmount = settlement.carriedInAmount,
                carryOverAmount = settlement.carryOverAmount
            )
        }
    }
}
