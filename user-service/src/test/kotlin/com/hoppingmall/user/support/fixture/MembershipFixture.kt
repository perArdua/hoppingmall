package com.hoppingmall.user.support.fixture

import com.hoppingmall.user.domain.Membership
import com.hoppingmall.user.domain.enums.MembershipGrade
import com.hoppingmall.user.support.withId
import java.math.BigDecimal

fun Membership.Companion.fixture(
    id: Long = 1L,
    userId: Long = 1L,
    grade: MembershipGrade = MembershipGrade.BRONZE,
    totalSpent: BigDecimal = BigDecimal.ZERO
): Membership = Membership(
    userId = userId,
    grade = grade,
    totalSpent = totalSpent
).withId(id)
