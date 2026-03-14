package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.point.domain.PointPolicy
import com.hoppingmall.mall.support.withId
import java.math.BigDecimal

fun PointPolicy.Companion.fixture(
    policyName: String = "테스트 정책",
    earnRate: BigDecimal = BigDecimal("0.01"),
    maxEarnRate: BigDecimal = BigDecimal("0.02"),
    minUseAmount: BigDecimal = BigDecimal("1000"),
    maxUseAmount: BigDecimal = BigDecimal("10000"),
    isActive: Boolean = false,
    description: String? = "테스트용 정책"
): PointPolicy {
    val policy = PointPolicy.create(
        policyName = policyName,
        earnRate = earnRate,
        maxEarnRate = maxEarnRate,
        minUseAmount = minUseAmount,
        maxUseAmount = maxUseAmount,
        description = description
    )
    
    val result = if (isActive) policy.activate() else policy
    return result.withId(1L)
}

fun PointPolicy.Companion.activeFixture(
    policyName: String = "활성 정책",
    earnRate: BigDecimal = BigDecimal("0.01"),
    maxEarnRate: BigDecimal = BigDecimal("0.02"),
    minUseAmount: BigDecimal = BigDecimal("1000"),
    maxUseAmount: BigDecimal = BigDecimal("10000"),
    description: String? = "활성화된 정책"
): PointPolicy {
    return PointPolicy.fixture(
        policyName = policyName,
        earnRate = earnRate,
        maxEarnRate = maxEarnRate,
        minUseAmount = minUseAmount,
        maxUseAmount = maxUseAmount,
        isActive = true,
        description = description
    )
}

fun PointPolicy.Companion.inactiveFixture(
    policyName: String = "비활성 정책",
    earnRate: BigDecimal = BigDecimal("0.01"),
    maxEarnRate: BigDecimal = BigDecimal("0.02"),
    minUseAmount: BigDecimal = BigDecimal("1000"),
    maxUseAmount: BigDecimal = BigDecimal("10000"),
    description: String? = "비활성화된 정책"
): PointPolicy {
    return PointPolicy.fixture(
        policyName = policyName,
        earnRate = earnRate,
        maxEarnRate = maxEarnRate,
        minUseAmount = minUseAmount,
        maxUseAmount = maxUseAmount,
        isActive = false,
        description = description
    )
} 