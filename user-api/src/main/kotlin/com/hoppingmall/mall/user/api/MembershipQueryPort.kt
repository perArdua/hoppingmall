package com.hoppingmall.mall.user.api

import java.math.BigDecimal

interface MembershipQueryPort {
    fun getPointEarnRate(userId: Long): BigDecimal
}
