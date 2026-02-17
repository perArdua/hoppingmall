package com.hoppingmall.mall.shipping.service

import com.hoppingmall.mall.shipping.dto.request.ShippingCreateRequest
import com.hoppingmall.mall.shipping.dto.request.ShippingStatusUpdateRequest
import com.hoppingmall.mall.shipping.dto.response.ShippingResponse

interface ShippingCommandService {
    fun createShipping(sellerId: Long, request: ShippingCreateRequest): ShippingResponse
    fun updateShippingStatus(sellerId: Long, shippingId: Long, request: ShippingStatusUpdateRequest): ShippingResponse
}
