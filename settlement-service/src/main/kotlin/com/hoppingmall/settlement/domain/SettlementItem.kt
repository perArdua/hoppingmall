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
    name = "settlement_items",
    indexes = [
        Index(name = "idx_settlement_items_settlement_id", columnList = "settlementId"),
        Index(name = "idx_settlement_items_order_id", columnList = "orderId")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = SettlementItem.ORDER_ITEM_UNIQUE_CONSTRAINT, columnNames = ["orderItemId"])
    ]
)
class SettlementItem private constructor(
    @Column(nullable = false)
    val settlementId: Long,

    @Column(nullable = false)
    val orderId: Long,

    @Column(nullable = false)
    val orderItemId: Long,

    @Column(nullable = false)
    val productName: String,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false, precision = 10, scale = 2)
    val salesAmount: BigDecimal
) : BaseEntity() {

    companion object {
        const val ORDER_ITEM_UNIQUE_CONSTRAINT = "uk_settlement_items_order_item_id"

        fun create(
            settlementId: Long,
            orderId: Long,
            orderItemId: Long,
            productName: String,
            quantity: Int,
            salesAmount: BigDecimal
        ): SettlementItem {
            return SettlementItem(
                settlementId = settlementId,
                orderId = orderId,
                orderItemId = orderItemId,
                productName = productName,
                quantity = quantity,
                salesAmount = salesAmount
            )
        }
    }
}
