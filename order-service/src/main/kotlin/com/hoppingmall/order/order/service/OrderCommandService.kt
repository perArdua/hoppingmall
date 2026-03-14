package com.hoppingmall.order.order.service

import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.dto.request.OrderStatusUpdateRequest
import com.hoppingmall.order.order.dto.response.OrderResponse

interface OrderCommandService {
    fun createOrder(buyerId: Long, request: OrderCreateRequest): OrderResponse
    fun cancelOrder(buyerId: Long, orderId: Long): OrderResponse
    fun updateOrderStatus(orderId: Long, request: OrderStatusUpdateRequest, userId: Long, isAdmin: Boolean): OrderResponse
}
