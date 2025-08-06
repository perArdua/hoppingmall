package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.point.domain.Point
import com.hoppingmall.mall.support.withId
import java.math.BigDecimal

fun Point.Companion.fixture(
    userId: Long = 1L,
    balance: BigDecimal = BigDecimal("1000")
): Point {
    return Point(userId = userId, balance = balance).withId(1L)
}

fun Point.Companion.emptyFixture(
    userId: Long = 1L
): Point {
    return Point.fixture(userId = userId, balance = BigDecimal.ZERO)
}

fun Point.Companion.richFixture(
    userId: Long = 1L,
    balance: BigDecimal = BigDecimal("10000")
): Point {
    return Point.fixture(userId = userId, balance = balance)
} 