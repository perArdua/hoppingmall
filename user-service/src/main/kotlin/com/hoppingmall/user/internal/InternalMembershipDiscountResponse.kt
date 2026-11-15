package com.hoppingmall.user.internal

import com.hoppingmall.user.domain.enums.MembershipGrade
import java.math.BigDecimal

data class InternalMembershipDiscountResponse(
    val grade: MembershipGrade,
    val discountRate: BigDecimal
) {
    companion object {
        fun of(grade: MembershipGrade) = InternalMembershipDiscountResponse(grade = grade, discountRate = grade.discountRate)
    }
}
