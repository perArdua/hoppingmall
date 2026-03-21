package com.hoppingmall.user.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.user.domain.enums.MembershipGrade
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "memberships")
class Membership(
    @Column(nullable = false, unique = true)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var grade: MembershipGrade = MembershipGrade.BRONZE,

    @Column(nullable = false, precision = 15, scale = 2)
    var totalSpent: BigDecimal = BigDecimal.ZERO
) : BaseEntity() {

    companion object {
        fun create(userId: Long): Membership {
            return Membership(userId = userId)
        }
    }

    fun addPurchaseAmount(amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "구매 금액은 0보다 커야 합니다" }
        this.totalSpent = this.totalSpent.add(amount)
        upgradeGradeIfEligible()
    }

    fun upgradeGradeIfEligible() {
        val newGrade = MembershipGrade.fromTotalSpent(totalSpent)
        if (newGrade.ordinal > this.grade.ordinal) {
            this.grade = newGrade
        }
    }
}
