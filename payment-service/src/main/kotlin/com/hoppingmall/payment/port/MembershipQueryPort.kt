package com.hoppingmall.payment.port

import java.math.BigDecimal

interface MembershipQueryPort {
    fun getPointEarningRate(userId: Long): BigDecimal
}
