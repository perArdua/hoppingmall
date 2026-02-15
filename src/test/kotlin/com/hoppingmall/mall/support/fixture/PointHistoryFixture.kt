package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.point.domain.PointHistory
import com.hoppingmall.mall.point.enum.PointType
import com.hoppingmall.mall.support.withId
import java.math.BigDecimal

fun PointHistory.Companion.fixture(
    userId: Long = 1L,
    amount: BigDecimal = BigDecimal("100"),
    type: PointType = PointType.EARN,
    reason: String? = "결제 완료",
    orderId: Long? = 1L,
    paymentId: Long? = 1L,
    eventId: String? = null
): PointHistory {
    return PointHistory(
        userId = userId,
        amount = amount,
        type = type,
        reason = reason,
        orderId = orderId,
        paymentId = paymentId,
        eventId = eventId
    ).withId(1L)
}

fun PointHistory.Companion.earnFixture(
    userId: Long = 1L,
    amount: BigDecimal = BigDecimal("100"),
    reason: String = "결제 완료",
    orderId: Long = 1L,
    paymentId: Long = 1L
): PointHistory {
    return PointHistory.fixture(
        userId = userId,
        amount = amount,
        type = PointType.EARN,
        reason = reason,
        orderId = orderId,
        paymentId = paymentId
    )
}

fun PointHistory.Companion.useFixture(
    userId: Long = 1L,
    amount: BigDecimal = BigDecimal("50"),
    reason: String = "상품 구매",
    orderId: Long = 1L
): PointHistory {
    return PointHistory.fixture(
        userId = userId,
        amount = amount.negate(),
        type = PointType.USE,
        reason = reason,
        orderId = orderId,
        paymentId = null
    )
} 
