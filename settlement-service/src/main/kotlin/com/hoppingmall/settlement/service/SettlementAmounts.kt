package com.hoppingmall.settlement.service

import java.math.BigDecimal
import java.math.RoundingMode

data class SettlementAmounts(
    val totalSalesAmount: BigDecimal,
    val totalRefundAmount: BigDecimal,
    val commissionAmount: BigDecimal,
    val carriedInAmount: BigDecimal,
    val settlementAmount: BigDecimal,
    val carryOverAmount: BigDecimal
) {
    companion object {
        fun calculate(
            totalSalesAmount: BigDecimal,
            totalRefundAmount: BigDecimal,
            commissionRate: BigDecimal,
            carriedInAmount: BigDecimal
        ): SettlementAmounts {
            val netSalesAmount = totalSalesAmount.subtract(totalRefundAmount)
            val commissionAmount = netSalesAmount.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP)
            val payable = netSalesAmount.subtract(commissionAmount).subtract(carriedInAmount)
            return SettlementAmounts(
                totalSalesAmount = totalSalesAmount,
                totalRefundAmount = totalRefundAmount,
                commissionAmount = commissionAmount,
                carriedInAmount = carriedInAmount,
                settlementAmount = payable.max(BigDecimal.ZERO),
                carryOverAmount = payable.negate().max(BigDecimal.ZERO)
            )
        }
    }
}
