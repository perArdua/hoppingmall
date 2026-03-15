package com.hoppingmall.settlement.dto.response

import com.hoppingmall.settlement.domain.Settlement
import com.hoppingmall.settlement.domain.SettlementItem
import com.hoppingmall.settlement.enums.SettlementStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class SettlementDetailResponse(
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
    val items: List<SettlementItemResponse>
) {
    companion object {
        fun from(settlement: Settlement, items: List<SettlementItem>): SettlementDetailResponse {
            return SettlementDetailResponse(
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
                items = items.map { SettlementItemResponse.from(it) }
            )
        }
    }
}

data class SettlementItemResponse(
    val id: Long,
    val orderId: Long,
    val orderItemId: Long,
    val productName: String,
    val quantity: Int,
    val salesAmount: BigDecimal
) {
    companion object {
        fun from(item: SettlementItem): SettlementItemResponse {
            return SettlementItemResponse(
                id = item.id!!,
                orderId = item.orderId,
                orderItemId = item.orderItemId,
                productName = item.productName,
                quantity = item.quantity,
                salesAmount = item.salesAmount
            )
        }
    }
}
