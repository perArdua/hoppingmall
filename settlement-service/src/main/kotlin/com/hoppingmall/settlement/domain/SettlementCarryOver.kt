package com.hoppingmall.settlement.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.settlement.exception.SettlementInvalidStatusException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal

@Entity
@Table(
    name = "settlement_carry_overs",
    indexes = [
        Index(name = "idx_settlement_carry_overs_seller_applied", columnList = "sellerId, appliedSettlementId")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_settlement_carry_overs_source_settlement_id", columnNames = ["sourceSettlementId"])
    ]
)
class SettlementCarryOver private constructor(
    @Column(nullable = false)
    val sellerId: Long,

    @Column(nullable = false)
    val sourceSettlementId: Long,

    @Column(nullable = false, precision = 12, scale = 2)
    val amount: BigDecimal
) : BaseEntity() {

    @Column
    var appliedSettlementId: Long? = null
        private set

    fun applyTo(settlementId: Long) {
        if (appliedSettlementId != null) {
            throw SettlementInvalidStatusException()
        }
        appliedSettlementId = settlementId
    }

    companion object {
        fun create(sellerId: Long, sourceSettlementId: Long, amount: BigDecimal): SettlementCarryOver =
            SettlementCarryOver(sellerId = sellerId, sourceSettlementId = sourceSettlementId, amount = amount)
    }
}
