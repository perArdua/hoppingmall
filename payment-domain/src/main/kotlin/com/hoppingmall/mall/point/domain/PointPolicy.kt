package com.hoppingmall.mall.point.domain

import com.hoppingmall.mall.global.common.entity.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "point_policies")
data class PointPolicy(
    @Column(nullable = false, unique = true)
    val policyName: String,
    
    @Column(nullable = false, precision = 5, scale = 4)
    val earnRate: BigDecimal, // 적립률 (예: 0.01 = 1%)
    
    @Column(nullable = false, precision = 5, scale = 4)
    val maxEarnRate: BigDecimal, // 최대 적립률
    
    @Column(nullable = false, precision = 10, scale = 2)
    val minUseAmount: BigDecimal, // 최소 사용 금액
    
    @Column(nullable = false, precision = 10, scale = 2)
    val maxUseAmount: BigDecimal, // 최대 사용 금액
    
    @Column(nullable = false)
    val isActive: Boolean = false, // 불변성 유지를 위해 val로 변경
    
    @Column
    val description: String? = null
) : BaseEntity() {

    init {
        validatePolicy()
    }

    private fun validatePolicy() {
        require(earnRate > BigDecimal.ZERO) { "적립률은 0보다 커야 합니다" }
        require(maxEarnRate > BigDecimal.ZERO) { "최대 적립률은 0보다 커야 합니다" }
        require(earnRate <= maxEarnRate) { "적립률은 최대 적립률을 초과할 수 없습니다" }
        require(minUseAmount >= BigDecimal.ZERO) { "최소 사용 금액은 0 이상이어야 합니다" }
        require(maxUseAmount > BigDecimal.ZERO) { "최대 사용 금액은 0보다 커야 합니다" }
        require(minUseAmount <= maxUseAmount) { "최소 사용 금액은 최대 사용 금액을 초과할 수 없습니다" }
    }

    fun activate(): PointPolicy {
        return this.copy(isActive = true)
    }

    fun deactivate(): PointPolicy {
        return this.copy(isActive = false)
    }

    fun update(
        policyName: String,
        earnRate: BigDecimal,
        maxEarnRate: BigDecimal,
        minUseAmount: BigDecimal,
        maxUseAmount: BigDecimal,
        description: String?
    ): PointPolicy {
        return this.copy(
            policyName = policyName,
            earnRate = earnRate,
            maxEarnRate = maxEarnRate,
            minUseAmount = minUseAmount,
            maxUseAmount = maxUseAmount,
            description = description
        )
    }

    fun calculateEarnPoints(purchaseAmount: BigDecimal): BigDecimal {
        require(purchaseAmount > BigDecimal.ZERO) { "구매 금액은 0보다 커야 합니다" }
        return purchaseAmount * earnRate
    }

    fun canUsePoints(useAmount: BigDecimal): Boolean {
        return useAmount >= minUseAmount && useAmount <= maxUseAmount
    }

    companion object {
        fun create(
            policyName: String,
            earnRate: BigDecimal,
            maxEarnRate: BigDecimal,
            minUseAmount: BigDecimal,
            maxUseAmount: BigDecimal,
            description: String? = null
        ): PointPolicy {
            return PointPolicy(
                policyName = policyName,
                earnRate = earnRate,
                maxEarnRate = maxEarnRate,
                minUseAmount = minUseAmount,
                maxUseAmount = maxUseAmount,
                isActive = false, // 기본적으로 비활성 상태로 생성
                description = description
            )
        }
    }
} 