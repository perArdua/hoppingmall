package com.hoppingmall.order.shipping.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class ShippingCreateRequest(
    @field:NotNull
    val orderId: Long,

    @field:NotBlank
    val carrierName: String,

    @field:NotBlank
    val trackingNumber: String,

    @field:NotBlank
    val recipientName: String,

    @field:NotBlank
    val recipientPhone: String,

    @field:NotBlank
    val recipientAddress: String
)
