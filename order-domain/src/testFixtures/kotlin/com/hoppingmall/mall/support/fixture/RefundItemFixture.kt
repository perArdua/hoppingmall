package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.refund.domain.RefundItem
import com.hoppingmall.mall.support.withId
import java.math.BigDecimal

fun RefundItem.Companion.fixture(
    refundId: Long = 1L,
    orderItemId: Long = 1L,
    productId: Long = 100L,
    productName: String = "테스트 상품",
    productPrice: BigDecimal = BigDecimal("15000"),
    quantity: Int = 2
): RefundItem {
    return RefundItem.create(
        refundId = refundId,
        orderItemId = orderItemId,
        productId = productId,
        productName = productName,
        productPrice = productPrice,
        quantity = quantity
    ).withId(1L)
}
