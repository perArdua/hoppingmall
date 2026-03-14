package com.hoppingmall.order.shipping.domain

import com.hoppingmall.order.common.BaseEntity
import com.hoppingmall.order.shipping.enum.ShippingStatus
import com.hoppingmall.order.shipping.exception.ShippingInvalidStatusException
import jakarta.persistence.*
import org.hibernate.annotations.Filter

@Entity
@Table(
    name = "shippings",
    indexes = [
        Index(name = "idx_shippings_order_id", columnList = "orderId"),
        Index(name = "idx_shippings_buyer_id", columnList = "buyerId")
    ]
)
@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
class Shipping private constructor(
    @Column(nullable = false)
    val orderId: Long,

    @Column(nullable = false)
    val buyerId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ShippingStatus = ShippingStatus.PREPARING,

    @Column(nullable = false)
    val carrierName: String,

    @Column(nullable = false)
    val trackingNumber: String,

    @Column(nullable = false)
    val recipientName: String,

    @Column(nullable = false)
    val recipientPhone: String,

    @Column(nullable = false)
    val recipientAddress: String
) : BaseEntity() {

    companion object {
        private val allowedTransitions: Map<ShippingStatus, Set<ShippingStatus>> = mapOf(
            ShippingStatus.PREPARING to setOf(ShippingStatus.IN_TRANSIT),
            ShippingStatus.IN_TRANSIT to setOf(ShippingStatus.DELIVERED),
            ShippingStatus.DELIVERED to emptySet()
        )

        fun create(
            orderId: Long,
            buyerId: Long,
            carrierName: String,
            trackingNumber: String,
            recipientName: String,
            recipientPhone: String,
            recipientAddress: String
        ): Shipping {
            return Shipping(
                orderId = orderId,
                buyerId = buyerId,
                carrierName = carrierName,
                trackingNumber = trackingNumber,
                recipientName = recipientName,
                recipientPhone = recipientPhone,
                recipientAddress = recipientAddress
            )
        }
    }

    fun updateStatus(newStatus: ShippingStatus) {
        val allowed = allowedTransitions[this.status] ?: emptySet()
        if (newStatus !in allowed) {
            throw ShippingInvalidStatusException()
        }
        this.status = newStatus
    }
}
