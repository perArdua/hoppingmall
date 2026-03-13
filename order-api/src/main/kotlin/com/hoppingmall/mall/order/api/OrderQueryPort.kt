package com.hoppingmall.mall.order.api

interface OrderQueryPort {
    fun isDelivered(orderId: Long, buyerId: Long): Boolean
    fun findOrderItemById(orderItemId: Long): OrderItemInfo?
    fun findOrderItemsByOrderId(orderId: Long): List<OrderItemInfo>
}
