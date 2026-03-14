package com.hoppingmall.payment.port

interface OrderQueryPort {
    fun findOrderItemsByOrderId(orderId: Long): List<OrderItemInfo>
}
