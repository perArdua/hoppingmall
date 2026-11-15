package com.hoppingmall.settlement.dto.response

import com.hoppingmall.settlement.domain.Settlement
import com.hoppingmall.settlement.domain.SettlementItem
import com.hoppingmall.settlement.domain.SettlementRefund
import com.hoppingmall.settlement.enums.SettlementStatus
import io.swagger.v3.oas.annotations.media.Schema
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
    val items: List<SettlementItemResponse>,
    @field:Schema(description = "앞 정산에서 이월되어 이번 지급액에서 뺀 금액. 0 이상.")
    val carriedInAmount: BigDecimal = BigDecimal.ZERO,
    @field:Schema(description = "이번 정산에서 지급액(0 바닥)으로 다 빼지 못해 다음 정산으로 넘긴 금액. 0 이상.")
    val carryOverAmount: BigDecimal = BigDecimal.ZERO,
    @field:Schema(description = "이 정산에서 차감한 완료 환불. 한 환불은 한 정산에서만 차감한다.")
    val refunds: List<SettlementRefundResponse> = emptyList()
) {
    companion object {
        fun from(
            settlement: Settlement,
            items: List<SettlementItem>,
            refunds: List<SettlementRefund> = emptyList()
        ): SettlementDetailResponse {
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
                items = items.map { SettlementItemResponse.from(it) },
                carriedInAmount = settlement.carriedInAmount,
                carryOverAmount = settlement.carryOverAmount,
                refunds = refunds.map { SettlementRefundResponse.from(it) }
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

data class SettlementRefundResponse(
    val refundId: Long,
    val refundAmount: BigDecimal
) {
    companion object {
        fun from(refund: SettlementRefund): SettlementRefundResponse =
            SettlementRefundResponse(refundId = refund.refundId, refundAmount = refund.refundAmount)
    }
}
