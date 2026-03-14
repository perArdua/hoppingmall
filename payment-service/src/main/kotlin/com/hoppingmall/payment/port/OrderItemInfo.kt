package com.hoppingmall.payment.port

data class OrderItemInfo(
    val id: Long,
    val productId: Long,
    val quantity: Int,
    val totalPrice: java.math.BigDecimal
)
