package com.hoppingmall.order.order.dto.response

import com.hoppingmall.order.order.domain.OrderShippingAddress

data class OrderShippingAddressResponse(
    val recipientName: String,
    val recipientPhone: String,
    val zipCode: String,
    val address: String,
    val addressDetail: String?,
    val deliveryMemo: String?
) {
    override fun toString(): String = "OrderShippingAddressResponse(masked)"

    companion object {
        fun from(address: OrderShippingAddress): OrderShippingAddressResponse =
            OrderShippingAddressResponse(
                recipientName = address.recipientName,
                recipientPhone = address.recipientPhone,
                zipCode = address.zipCode,
                address = address.address,
                addressDetail = address.addressDetail,
                deliveryMemo = address.deliveryMemo
            )
    }
}
