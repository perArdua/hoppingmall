package com.hoppingmall.user.domain.enums

import java.math.BigDecimal

enum class MembershipGrade(
    val gradeName: String,
    val requiredAmount: BigDecimal,
    val pointEarningRate: BigDecimal,
    val discountRate: BigDecimal
) {
    BRONZE("브론즈", BigDecimal.ZERO, BigDecimal("0.01"), BigDecimal.ZERO),
    SILVER("실버", BigDecimal("100000"), BigDecimal("0.02"), BigDecimal("0.01")),
    GOLD("골드", BigDecimal("500000"), BigDecimal("0.03"), BigDecimal("0.02")),
    PLATINUM("플래티넘", BigDecimal("1000000"), BigDecimal("0.05"), BigDecimal("0.03")),
    DIAMOND("다이아몬드", BigDecimal("5000000"), BigDecimal("0.07"), BigDecimal("0.05"));

    fun nextGrade(): MembershipGrade? {
        val grades = entries
        val currentIndex = grades.indexOf(this)
        return if (currentIndex < grades.size - 1) grades[currentIndex + 1] else null
    }

    companion object {
        fun fromTotalSpent(totalSpent: BigDecimal): MembershipGrade {
            return entries.reversed().first { totalSpent >= it.requiredAmount }
        }
    }
}
