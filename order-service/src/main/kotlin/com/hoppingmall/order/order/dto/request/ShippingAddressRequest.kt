package com.hoppingmall.order.order.dto.request

import com.hoppingmall.order.order.domain.OrderShippingAddress
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class ShippingAddressRequest(
    @field:NotBlank(message = "받는 사람 이름은 필수입니다.")
    @field:Size(max = 50, message = "받는 사람 이름은 50자 이하여야 합니다.")
    val recipientName: String,

    @field:NotBlank(message = "받는 사람 전화번호는 필수입니다.")
    @field:Pattern(regexp = PHONE_PATTERN, message = "받는 사람 전화번호 형식이 올바르지 않습니다.")
    val recipientPhone: String,

    @field:NotBlank(message = "우편번호는 필수입니다.")
    @field:Pattern(regexp = ZIP_CODE_PATTERN, message = "우편번호는 숫자 5자리여야 합니다.")
    val zipCode: String,

    @field:NotBlank(message = "주소는 필수입니다.")
    @field:Size(max = 150, message = "주소는 150자 이하여야 합니다.")
    val address: String,

    @field:Size(max = 90, message = "상세 주소는 90자 이하여야 합니다.")
    val addressDetail: String? = null,

    @field:Size(max = 200, message = "배송 메모는 200자 이하여야 합니다.")
    val deliveryMemo: String? = null
) {
    fun toShippingAddress(): OrderShippingAddress =
        OrderShippingAddress(
            recipientName = recipientName.trim(),
            recipientPhone = recipientPhone.trim(),
            zipCode = zipCode.trim(),
            address = address.trim(),
            addressDetail = addressDetail?.trim()?.takeIf { it.isNotEmpty() },
            deliveryMemo = deliveryMemo?.trim()?.takeIf { it.isNotEmpty() }
        )

    override fun toString(): String = "ShippingAddressRequest(masked)"

    companion object {
        const val PHONE_PATTERN = "^0\\d{1,2}-?\\d{3,4}-?\\d{4}$"
        const val ZIP_CODE_PATTERN = "^\\d{5}$"
    }
}
