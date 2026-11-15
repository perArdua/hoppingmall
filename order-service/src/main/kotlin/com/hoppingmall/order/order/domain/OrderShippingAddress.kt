package com.hoppingmall.order.order.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class OrderShippingAddress(
    @Column(name = "shipping_recipient_name", length = 50)
    val recipientName: String,

    @Column(name = "shipping_recipient_phone", length = 20)
    val recipientPhone: String,

    @Column(name = "shipping_zip_code", length = 5)
    val zipCode: String,

    @Column(name = "shipping_address", length = 150)
    val address: String,

    @Column(name = "shipping_address_detail", length = 90)
    val addressDetail: String? = null,

    @Column(name = "shipping_delivery_memo", length = 200)
    val deliveryMemo: String? = null
) {
    fun toRecipientAddress(): String =
        listOfNotNull("($zipCode)", address, addressDetail?.takeIf { it.isNotBlank() }).joinToString(" ")

    override fun toString(): String = "OrderShippingAddress(masked)"
}
