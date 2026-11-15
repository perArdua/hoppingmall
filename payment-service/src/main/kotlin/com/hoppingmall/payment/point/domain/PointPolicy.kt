package com.hoppingmall.payment.point.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(
    name = "point_policies",
    uniqueConstraints = [UniqueConstraint(name = PointPolicy.ACTIVE_KEY_CONSTRAINT, columnNames = ["active_key"])]
)
class PointPolicy private constructor(
    policyName: String,
    earnRate: BigDecimal,
    maxEarnRate: BigDecimal,
    minUseAmount: BigDecimal,
    maxUseAmount: BigDecimal,
    description: String?
) : BaseEntity() {

    @Column(nullable = false, unique = true)
    var policyName: String = policyName
        private set

    @Column(nullable = false, precision = 5, scale = 4)
    var earnRate: BigDecimal = earnRate
        private set

    @Column(nullable = false, precision = 5, scale = 4)
    var maxEarnRate: BigDecimal = maxEarnRate
        private set

    @Column(nullable = false, precision = 10, scale = 2)
    var minUseAmount: BigDecimal = minUseAmount
        private set

    @Column(nullable = false, precision = 10, scale = 2)
    var maxUseAmount: BigDecimal = maxUseAmount
        private set

    @Column(nullable = false)
    var isActive: Boolean = false
        private set

    @Column
    var description: String? = description
        private set

    @Column(
        name = "active_key",
        insertable = false,
        updatable = false,
        columnDefinition = "TINYINT GENERATED ALWAYS AS (CASE WHEN is_active THEN 1 END)"
    )
    private val activeKey: Int? = null

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

    fun activate() {
        isActive = true
    }

    fun deactivate() {
        isActive = false
    }

    fun update(
        policyName: String,
        earnRate: BigDecimal,
        maxEarnRate: BigDecimal,
        minUseAmount: BigDecimal,
        maxUseAmount: BigDecimal,
        description: String?
    ) {
        this.policyName = policyName
        this.earnRate = earnRate
        this.maxEarnRate = maxEarnRate
        this.minUseAmount = minUseAmount
        this.maxUseAmount = maxUseAmount
        this.description = description
        validatePolicy()
    }

    fun calculateEarnPoints(purchaseAmount: BigDecimal): BigDecimal {
        require(purchaseAmount > BigDecimal.ZERO) { "구매 금액은 0보다 커야 합니다" }
        return purchaseAmount * earnRate
    }

    fun canUsePoints(useAmount: BigDecimal): Boolean =
        useAmount >= minUseAmount && useAmount <= maxUseAmount

    companion object {
        const val ACTIVE_KEY_CONSTRAINT = "uk_point_policies_active_key"

        fun create(
            policyName: String,
            earnRate: BigDecimal,
            maxEarnRate: BigDecimal,
            minUseAmount: BigDecimal,
            maxUseAmount: BigDecimal,
            description: String? = null
        ): PointPolicy =
            PointPolicy(
                policyName = policyName,
                earnRate = earnRate,
                maxEarnRate = maxEarnRate,
                minUseAmount = minUseAmount,
                maxUseAmount = maxUseAmount,
                description = description
            )
    }
}
