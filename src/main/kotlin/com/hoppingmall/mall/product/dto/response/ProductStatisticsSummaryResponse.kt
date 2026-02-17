package com.hoppingmall.mall.product.dto.response

import java.math.BigDecimal

data class ProductStatisticsSummaryResponse(
    val totalProductCount: Long,
    val totalSalesAmount: BigDecimal,
    val totalRefundAmount: BigDecimal,
    val averageRefundRate: BigDecimal
)
