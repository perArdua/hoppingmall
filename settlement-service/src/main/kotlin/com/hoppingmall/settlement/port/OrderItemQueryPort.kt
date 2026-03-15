package com.hoppingmall.settlement.port

import java.math.BigDecimal
import java.time.LocalDateTime

interface OrderItemQueryPort {
    fun findDeliveredItemsBySellerAndPeriod(
        sellerId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<OrderItemInfo>
}

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
