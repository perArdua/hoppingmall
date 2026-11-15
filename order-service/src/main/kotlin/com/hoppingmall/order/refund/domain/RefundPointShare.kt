package com.hoppingmall.order.refund.domain

import java.math.BigDecimal
import java.math.RoundingMode

object RefundPointShare {

    fun of(
        usedPoints: BigDecimal,
        orderTotal: BigDecimal,
        refundedGross: BigDecimal,
        alreadyRefundedPoints: BigDecimal,
        isFullRefund: Boolean
    ): BigDecimal {
        val remaining = usedPoints.subtract(alreadyRefundedPoints).max(BigDecimal.ZERO)
        if (isFullRefund) return remaining
        if (usedPoints.signum() <= 0 || orderTotal.signum() <= 0) return BigDecimal.ZERO
        return usedPoints.multiply(refundedGross)
            .divide(orderTotal, 0, RoundingMode.DOWN)
            .min(remaining)
    }
}
