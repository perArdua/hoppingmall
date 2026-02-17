package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.membership.domain.Membership
import com.hoppingmall.mall.membership.enum.MembershipGrade
import com.hoppingmall.mall.support.withId
import java.math.BigDecimal

fun Membership.Companion.fixture(
    userId: Long = 1L,
    grade: MembershipGrade = MembershipGrade.BRONZE,
    totalSpent: BigDecimal = BigDecimal.ZERO
): Membership {
    return Membership(userId = userId, grade = grade, totalSpent = totalSpent).withId(1L)
}
