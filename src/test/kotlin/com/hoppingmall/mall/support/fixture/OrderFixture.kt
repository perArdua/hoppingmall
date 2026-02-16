package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.order.domain.Order
import com.hoppingmall.mall.order.enum.OrderStatus
import com.hoppingmall.mall.support.withId
import java.math.BigDecimal

fun Order.Companion.fixture(
    buyerId: Long = 1L,
    totalAmount: BigDecimal = BigDecimal("50000"),
    status: OrderStatus = OrderStatus.CREATED
): Order {
    return Order.create(
        buyerId = buyerId,
        totalAmount = totalAmount
    ).apply {
        this.status = status
    }.withId(1L)
}

fun Order.Companion.paidFixture(
    buyerId: Long = 1L,
    totalAmount: BigDecimal = BigDecimal("50000")
): Order {
    return Order.fixture(
        buyerId = buyerId,
        totalAmount = totalAmount,
        status = OrderStatus.PAID
    )
}

fun Order.Companion.cancelledFixture(
    buyerId: Long = 1L,
    totalAmount: BigDecimal = BigDecimal("50000")
): Order {
    return Order.fixture(
        buyerId = buyerId,
        totalAmount = totalAmount,
        status = OrderStatus.CANCELLED
    )
}
