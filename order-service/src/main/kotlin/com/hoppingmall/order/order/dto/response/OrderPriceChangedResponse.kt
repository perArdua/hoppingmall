package com.hoppingmall.order.order.dto.response

import java.math.BigDecimal

data class OrderPriceChangedResponse(
    val items: List<OrderPriceChange>
)

data class OrderPriceChange(
    val cartItemId: Long,
    val productId: Long,
    val expectedPrice: BigDecimal,
    val currentPrice: BigDecimal
)
