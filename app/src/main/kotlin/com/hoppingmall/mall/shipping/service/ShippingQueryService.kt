package com.hoppingmall.mall.shipping.service

import com.hoppingmall.mall.shipping.dto.response.ShippingResponse

interface ShippingQueryService {
    fun getShippingByOrderId(orderId: Long): ShippingResponse
    fun getShipping(shippingId: Long): ShippingResponse
}
