package com.hoppingmall.payment.point.dto.response

import java.math.BigDecimal

data class PointUseResponse(
    val usedAmount: BigDecimal,
    val remainingBalance: BigDecimal,
    val orderId: Long
)
