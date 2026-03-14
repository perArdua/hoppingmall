package com.hoppingmall.order.order.service

import com.hoppingmall.order.order.dto.response.OrderResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice

interface OrderQueryService {
    fun getOrder(orderId: Long, buyerId: Long): OrderResponse
    fun getMyOrders(buyerId: Long, pageable: Pageable): Slice<OrderResponse>
}
