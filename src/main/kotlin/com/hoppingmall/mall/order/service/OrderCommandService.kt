package com.hoppingmall.mall.order.service

import com.hoppingmall.mall.order.dto.request.OrderCreateRequest
import com.hoppingmall.mall.order.dto.request.OrderStatusUpdateRequest
import com.hoppingmall.mall.order.dto.response.OrderResponse

interface OrderCommandService {
    fun createOrder(buyerId: Long, request: OrderCreateRequest): OrderResponse
    fun cancelOrder(buyerId: Long, orderId: Long): OrderResponse
    fun updateOrderStatus(orderId: Long, request: OrderStatusUpdateRequest): OrderResponse
}
