package com.hoppingmall.order.shipping.dto.request

import com.hoppingmall.order.order.dto.request.ShippingAddressRequest
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class ShippingCreateRequest(
    @field:NotNull
    val orderId: Long,

    @field:NotBlank
    val carrierName: String,

    @field:NotBlank
    val trackingNumber: String,

    @field:Size(max = 50, message = "받는 사람 이름은 50자 이하여야 합니다.")
    val recipientName: String? = null,

    @field:Pattern(regexp = ShippingAddressRequest.PHONE_PATTERN, message = "받는 사람 전화번호 형식이 올바르지 않습니다.")
    val recipientPhone: String? = null,

    @field:Size(max = 255, message = "받는 사람 주소는 255자 이하여야 합니다.")
    val recipientAddress: String? = null
) {
    override fun toString(): String =
        "ShippingCreateRequest(orderId=$orderId, carrierName=$carrierName, trackingNumber=$trackingNumber, recipient=masked)"
}
