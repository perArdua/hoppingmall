package com.hoppingmall.mall.shipping.dto.response

import com.hoppingmall.mall.shipping.domain.Shipping
import com.hoppingmall.mall.shipping.enum.ShippingStatus
import java.time.LocalDateTime

data class ShippingResponse(
    val id: Long,
    val orderId: Long,
    val buyerId: Long,
    val status: ShippingStatus,
    val carrierName: String,
    val trackingNumber: String,
    val recipientName: String,
    val recipientPhone: String,
    val recipientAddress: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun from(shipping: Shipping): ShippingResponse {
            return ShippingResponse(
                id = shipping.id!!,
                orderId = shipping.orderId,
                buyerId = shipping.buyerId,
                status = shipping.status,
                carrierName = shipping.carrierName,
                trackingNumber = shipping.trackingNumber,
                recipientName = shipping.recipientName,
                recipientPhone = shipping.recipientPhone,
                recipientAddress = shipping.recipientAddress,
                createdAt = shipping.createdAt,
                updatedAt = shipping.updatedAt
            )
        }
    }
}
