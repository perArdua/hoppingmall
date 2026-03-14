package com.hoppingmall.payment.point.dto.response

import com.hoppingmall.payment.point.domain.PointPolicy
import java.math.BigDecimal

data class PointPolicyResponse(
    val id: Long,
    val policyName: String,
    val earnRate: BigDecimal,
    val maxEarnRate: BigDecimal,
    val minUseAmount: BigDecimal,
    val maxUseAmount: BigDecimal,
    val isActive: Boolean,
    val description: String?
) {
    companion object {
        fun from(policy: PointPolicy): PointPolicyResponse {
            return PointPolicyResponse(
                id = policy.id!!,
                policyName = policy.policyName,
                earnRate = policy.earnRate,
                maxEarnRate = policy.maxEarnRate,
                minUseAmount = policy.minUseAmount,
                maxUseAmount = policy.maxUseAmount,
                isActive = policy.isActive,
                description = policy.description
            )
        }
    }
}
