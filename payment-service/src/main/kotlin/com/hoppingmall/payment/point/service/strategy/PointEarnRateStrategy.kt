package com.hoppingmall.payment.point.service.strategy

import java.math.BigDecimal

fun interface PointEarnRateStrategy {
    fun getEarnRate(userId: Long): BigDecimal
}
