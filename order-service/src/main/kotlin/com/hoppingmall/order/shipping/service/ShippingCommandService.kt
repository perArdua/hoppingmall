package com.hoppingmall.order.shipping.service

import com.hoppingmall.order.shipping.dto.request.ShippingCreateRequest
import com.hoppingmall.order.shipping.dto.request.ShippingStatusUpdateRequest
import com.hoppingmall.order.shipping.dto.response.ShippingResponse

interface ShippingCommandService {
    fun createShipping(sellerId: Long, request: ShippingCreateRequest): ShippingResponse
    fun updateShippingStatus(sellerId: Long, shippingId: Long, request: ShippingStatusUpdateRequest): ShippingResponse
}
