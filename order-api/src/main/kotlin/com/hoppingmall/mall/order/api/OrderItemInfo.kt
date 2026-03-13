package com.hoppingmall.mall.order.api

import java.math.BigDecimal

data class OrderItemInfo(
    val id: Long,
    val orderId: Long,
    val sellerId: Long,
    val productId: Long,
    val productName: String,
    val productPrice: BigDecimal,
    val quantity: Int,
    val totalPrice: BigDecimal
)
