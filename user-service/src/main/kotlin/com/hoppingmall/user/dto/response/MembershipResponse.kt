package com.hoppingmall.user.dto.response

import com.hoppingmall.user.domain.Membership
import com.hoppingmall.user.domain.enums.MembershipGrade
import java.math.BigDecimal
import java.time.LocalDateTime

data class MembershipResponse(
    val id: Long,
    val userId: Long,
    val grade: MembershipGrade,
    val gradeName: String,
    val totalSpent: BigDecimal,
    val pointEarningRate: BigDecimal,
    val discountRate: BigDecimal,
    val nextGrade: MembershipGrade?,
    val amountToNextGrade: BigDecimal?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun from(membership: Membership): MembershipResponse {
            val nextGrade = membership.grade.nextGrade()
            val amountToNextGrade = nextGrade?.let {
                it.requiredAmount.subtract(membership.totalSpent).coerceAtLeast(BigDecimal.ZERO)
            }

            return MembershipResponse(
                id = membership.id!!,
                userId = membership.userId,
                grade = membership.grade,
                gradeName = membership.grade.gradeName,
                totalSpent = membership.totalSpent,
                pointEarningRate = membership.grade.pointEarningRate,
                discountRate = membership.grade.discountRate,
                nextGrade = nextGrade,
                amountToNextGrade = amountToNextGrade,
                createdAt = membership.createdAt,
                updatedAt = membership.updatedAt
            )
        }
    }
}
