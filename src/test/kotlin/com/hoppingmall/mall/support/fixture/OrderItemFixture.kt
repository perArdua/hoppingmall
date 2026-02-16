package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.order.domain.OrderItem
import com.hoppingmall.mall.support.withId
import java.math.BigDecimal

fun OrderItem.Companion.fixture(
    orderId: Long = 1L,
    productId: Long = 100L,
    productName: String = "테스트 상품",
    productPrice: BigDecimal = BigDecimal("15000"),
    quantity: Int = 2
): OrderItem {
    return OrderItem.create(
        orderId = orderId,
        productId = productId,
        productName = productName,
        productPrice = productPrice,
        quantity = quantity
    ).withId(1L)
}
