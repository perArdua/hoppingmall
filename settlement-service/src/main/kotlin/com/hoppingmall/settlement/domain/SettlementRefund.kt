package com.hoppingmall.settlement.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal

@Entity
@Table(
    name = "settlement_refunds",
    indexes = [
        Index(name = "idx_settlement_refunds_settlement_id", columnList = "settlementId")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = SettlementRefund.REFUND_UNIQUE_CONSTRAINT, columnNames = ["refundId"])
    ]
)
class SettlementRefund private constructor(
    @Column(nullable = false)
    val settlementId: Long,

    @Column(nullable = false)
    val refundId: Long,

    @Column(nullable = false, precision = 12, scale = 2)
    val refundAmount: BigDecimal
) : BaseEntity() {

    companion object {
        const val REFUND_UNIQUE_CONSTRAINT = "uk_settlement_refunds_refund_id"

        fun create(settlementId: Long, refundId: Long, refundAmount: BigDecimal): SettlementRefund =
            SettlementRefund(settlementId = settlementId, refundId = refundId, refundAmount = refundAmount)
    }
}
