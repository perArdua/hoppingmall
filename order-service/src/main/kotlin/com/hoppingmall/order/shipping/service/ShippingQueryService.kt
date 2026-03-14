package com.hoppingmall.order.shipping.service

import com.hoppingmall.order.shipping.dto.response.ShippingResponse

interface ShippingQueryService {
    fun getShippingByOrderId(orderId: Long): ShippingResponse
    fun getShipping(shippingId: Long): ShippingResponse
}
