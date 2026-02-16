package com.hoppingmall.mall.order.service

import com.hoppingmall.mall.order.dto.response.OrderResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface OrderQueryService {
    fun getOrder(orderId: Long, buyerId: Long): OrderResponse
    fun getMyOrders(buyerId: Long, pageable: Pageable): Page<OrderResponse>
}
