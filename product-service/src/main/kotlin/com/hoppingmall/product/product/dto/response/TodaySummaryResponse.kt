package com.hoppingmall.product.product.dto.response

import java.math.BigDecimal

data class TodaySummaryResponse(
    val todaySalesAmount: BigDecimal,
    val todayOrderCount: Long,
    val todayRefundAmount: BigDecimal
)
