package com.hoppingmall.order.shipping.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.common.EnumNameConverter
import com.hoppingmall.order.shipping.enum.ShippingStatus
import com.hoppingmall.order.shipping.exception.ShippingInvalidStatusException
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault

@Entity
@Table(
    name = "shippings",
    indexes = [
        Index(name = "idx_shippings_order_id", columnList = "orderId"),
        Index(name = "idx_shippings_buyer_id", columnList = "buyerId"),
        Index(name = "idx_shippings_tracking_number", columnList = "trackingNumber")
    ]
)
class Shipping private constructor(
    @Column(nullable = false)
    val orderId: Long,

    @Column(nullable = false)
    val buyerId: Long,

    @Convert(converter = ShippingStatusConverter::class)
    @Column(nullable = false, length = 32)
    var status: ShippingStatus = ShippingStatus.PREPARING,

    carrierName: String,
    trackingNumber: String,
    recipientName: String,
    recipientPhone: String,
    recipientAddress: String
) : BaseEntity() {

    @ColumnDefault("1")
    @Column(nullable = false)
    var attempt: Int = 1
        private set

    @Column(nullable = false)
    var carrierName: String = carrierName
        private set

    @Column(nullable = false)
    var trackingNumber: String = trackingNumber
        private set

    @Column(nullable = false)
    var recipientName: String = recipientName
        private set

    @Column(nullable = false)
    var recipientPhone: String = recipientPhone
        private set

    @Column(nullable = false)
    var recipientAddress: String = recipientAddress
        private set

    companion object {
        private val allowedTransitions: Map<ShippingStatus, Set<ShippingStatus>> = mapOf(
            ShippingStatus.PREPARING to setOf(ShippingStatus.IN_TRANSIT, ShippingStatus.CANCELLED),
            ShippingStatus.IN_TRANSIT to setOf(ShippingStatus.DELIVERED, ShippingStatus.FAILED),
            ShippingStatus.DELIVERED to emptySet(),
            ShippingStatus.FAILED to emptySet(),
            ShippingStatus.CANCELLED to emptySet()
        )

        private val restartableStatuses: Set<ShippingStatus> = setOf(ShippingStatus.FAILED, ShippingStatus.CANCELLED)

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

    fun canRestart(): Boolean = status in restartableStatuses

    fun restart(
        carrierName: String,
        trackingNumber: String,
        recipientName: String,
        recipientPhone: String,
        recipientAddress: String
    ) {
        if (!canRestart()) {
            throw ShippingInvalidStatusException()
        }
        this.status = ShippingStatus.PREPARING
        this.attempt += 1
        this.carrierName = carrierName
        this.trackingNumber = trackingNumber
        this.recipientName = recipientName
        this.recipientPhone = recipientPhone
        this.recipientAddress = recipientAddress
    }

    fun updateStatus(newStatus: ShippingStatus) {
        val allowed = allowedTransitions[this.status] ?: emptySet()
        if (newStatus !in allowed) {
            throw ShippingInvalidStatusException()
        }
        this.status = newStatus
    }
}

class ShippingStatusConverter : EnumNameConverter<ShippingStatus>(ShippingStatus::class.java)
