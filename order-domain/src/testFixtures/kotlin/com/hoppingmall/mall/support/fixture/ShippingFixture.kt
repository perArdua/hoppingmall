package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.shipping.domain.Shipping
import com.hoppingmall.mall.shipping.enum.ShippingStatus
import com.hoppingmall.mall.support.withId

fun Shipping.Companion.fixture(
    orderId: Long = 1L,
    buyerId: Long = 1L,
    carrierName: String = "CJ대한통운",
    trackingNumber: String = "1234567890",
    recipientName: String = "홍길동",
    recipientPhone: String = "010-1234-5678",
    recipientAddress: String = "서울시 강남구 테헤란로 123",
    status: ShippingStatus = ShippingStatus.PREPARING
): Shipping {
    return Shipping.create(
        orderId = orderId,
        buyerId = buyerId,
        carrierName = carrierName,
        trackingNumber = trackingNumber,
        recipientName = recipientName,
        recipientPhone = recipientPhone,
        recipientAddress = recipientAddress
    ).apply {
        this.status = status
    }.withId(1L)
}

fun Shipping.Companion.inTransitFixture(
    orderId: Long = 1L,
    buyerId: Long = 1L,
    trackingNumber: String = "1234567890"
): Shipping {
    return Shipping.fixture(
        orderId = orderId,
        buyerId = buyerId,
        trackingNumber = trackingNumber,
        status = ShippingStatus.IN_TRANSIT
    )
}

fun Shipping.Companion.deliveredFixture(
    orderId: Long = 1L,
    buyerId: Long = 1L,
    trackingNumber: String = "1234567890"
): Shipping {
    return Shipping.fixture(
        orderId = orderId,
        buyerId = buyerId,
        trackingNumber = trackingNumber,
        status = ShippingStatus.DELIVERED
    )
}
