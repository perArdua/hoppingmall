package com.hoppingmall.settlement.domain

import com.hoppingmall.settlement.common.BaseEntity
import com.hoppingmall.settlement.enums.SettlementStatus
import com.hoppingmall.settlement.exception.SettlementInvalidStatusException
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "settlements",
    indexes = [
        Index(name = "idx_settlements_seller_id", columnList = "sellerId"),
        Index(name = "idx_settlements_status", columnList = "status")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_settlements_seller_period", columnNames = ["sellerId", "periodStart", "periodEnd"])
    ]
)
class Settlement private constructor(
    @Column(nullable = false)
    val sellerId: Long,

    @Column(nullable = false)
    val periodStart: LocalDate,

    @Column(nullable = false)
    val periodEnd: LocalDate,

    @Column(nullable = false, precision = 12, scale = 2)
    val totalSalesAmount: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 2)
    val totalRefundAmount: BigDecimal,

    @Column(nullable = false, precision = 5, scale = 4)
    val commissionRate: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 2)
    val commissionAmount: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 2)
    val settlementAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SettlementStatus = SettlementStatus.CALCULATED,

    @Column
    var confirmedAt: LocalDateTime? = null,

    @Column
    var paidAt: LocalDateTime? = null
) : BaseEntity() {

    companion object {
        private val allowedTransitions: Map<SettlementStatus, Set<SettlementStatus>> = mapOf(
            SettlementStatus.CALCULATED to setOf(SettlementStatus.CONFIRMED),
            SettlementStatus.CONFIRMED to setOf(SettlementStatus.PAID),
            SettlementStatus.PAID to emptySet()
        )

        fun create(
            sellerId: Long,
            periodStart: LocalDate,
            periodEnd: LocalDate,
            totalSalesAmount: BigDecimal,
            totalRefundAmount: BigDecimal,
            commissionRate: BigDecimal,
            commissionAmount: BigDecimal,
            settlementAmount: BigDecimal
        ): Settlement {
            return Settlement(
                sellerId = sellerId,
                periodStart = periodStart,
                periodEnd = periodEnd,
                totalSalesAmount = totalSalesAmount,
                totalRefundAmount = totalRefundAmount,
                commissionRate = commissionRate,
                commissionAmount = commissionAmount,
                settlementAmount = settlementAmount
            )
        }
    }

    fun confirm() {
        validateTransition(SettlementStatus.CONFIRMED)
        this.status = SettlementStatus.CONFIRMED
        this.confirmedAt = LocalDateTime.now()
    }

    fun pay() {
        validateTransition(SettlementStatus.PAID)
        this.status = SettlementStatus.PAID
        this.paidAt = LocalDateTime.now()
    }

    private fun validateTransition(newStatus: SettlementStatus) {
        val allowed = allowedTransitions[this.status] ?: emptySet()
        if (newStatus !in allowed) {
            throw SettlementInvalidStatusException()
        }
    }
}
