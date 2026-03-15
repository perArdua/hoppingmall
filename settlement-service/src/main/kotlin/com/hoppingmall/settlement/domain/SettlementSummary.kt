package com.hoppingmall.settlement.domain

import com.hoppingmall.settlement.common.BaseEntity
import com.hoppingmall.settlement.enums.SettlementStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "settlement_summaries",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_settlement_summaries_seller_period",
            columnNames = ["sellerId", "periodStart", "periodEnd"]
        )
    ],
    indexes = [
        Index(name = "idx_settlement_summaries_seller_id", columnList = "sellerId")
    ]
)
class SettlementSummary(
    @Column(nullable = false)
    val settlementId: Long,

    @Column(nullable = false)
    val sellerId: Long,

    @Column(nullable = false)
    val periodStart: LocalDate,

    @Column(nullable = false)
    val periodEnd: LocalDate,

    @Column(nullable = false, precision = 12, scale = 2)
    var totalSalesAmount: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 2)
    var totalRefundAmount: BigDecimal,

    @Column(nullable = false, precision = 5, scale = 4)
    var commissionRate: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 2)
    var commissionAmount: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 2)
    var settlementAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SettlementStatus,

    @Column
    var confirmedAt: LocalDateTime? = null,

    @Column
    var paidAt: LocalDateTime? = null
) : BaseEntity() {

    companion object {
        fun from(settlement: Settlement): SettlementSummary {
            return SettlementSummary(
                settlementId = settlement.id!!,
                sellerId = settlement.sellerId,
                periodStart = settlement.periodStart,
                periodEnd = settlement.periodEnd,
                totalSalesAmount = settlement.totalSalesAmount,
                totalRefundAmount = settlement.totalRefundAmount,
                commissionRate = settlement.commissionRate,
                commissionAmount = settlement.commissionAmount,
                settlementAmount = settlement.settlementAmount,
                status = settlement.status,
                confirmedAt = settlement.confirmedAt,
                paidAt = settlement.paidAt
            )
        }
    }

    fun updateFrom(settlement: Settlement) {
        this.totalSalesAmount = settlement.totalSalesAmount
        this.totalRefundAmount = settlement.totalRefundAmount
        this.commissionRate = settlement.commissionRate
        this.commissionAmount = settlement.commissionAmount
        this.settlementAmount = settlement.settlementAmount
        this.status = settlement.status
        this.confirmedAt = settlement.confirmedAt
        this.paidAt = settlement.paidAt
    }
}
