package com.hoppingmall.mall.point.dto.response

import java.math.BigDecimal

data class PointUseResponse(
    val usedAmount: BigDecimal,
    val remainingBalance: BigDecimal,
    val orderId: Long
) 