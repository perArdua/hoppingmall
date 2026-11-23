package com.hoppingmall.order.shipping.service

import com.hoppingmall.order.shipping.dto.response.ShippingResponse

interface ShippingQueryService {
    fun getShippingByOrderId(orderId: Long, userId: Long, isAdmin: Boolean): ShippingResponse
    fun getShipping(shippingId: Long, userId: Long, isAdmin: Boolean): ShippingResponse
}
