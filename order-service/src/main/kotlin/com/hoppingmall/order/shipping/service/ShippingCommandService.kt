package com.hoppingmall.order.shipping.service

import com.hoppingmall.order.order.dto.response.OrderResponse
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.shipping.dto.request.ShippingCreateRequest
import com.hoppingmall.order.shipping.dto.request.ShippingStatusUpdateRequest
import com.hoppingmall.order.shipping.dto.response.ShippingResponse

interface ShippingCommandService {
    fun createShipping(sellerId: Long, request: ShippingCreateRequest, isAdmin: Boolean = false): ShippingResponse
    fun updateShippingStatus(
        sellerId: Long,
        shippingId: Long,
        request: ShippingStatusUpdateRequest,
        isAdmin: Boolean = false
    ): ShippingResponse
    fun updateStatusByOrder(userId: Long, orderId: Long, status: OrderStatus, isAdmin: Boolean): OrderResponse
}
