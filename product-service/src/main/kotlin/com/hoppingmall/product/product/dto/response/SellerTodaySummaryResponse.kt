package com.hoppingmall.product.product.dto.response

import java.math.BigDecimal

data class SellerTodaySummaryResponse(
    val totalProducts: Long,
    val todaySalesAmount: BigDecimal,
    val todayOrderCount: Long,
    val todayRefundAmount: BigDecimal,
    val topSellingProductId: Long?,
    val topSellingProductName: String?
)
