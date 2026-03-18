package com.hoppingmall.payment.refund.domain

import com.hoppingmall.payment.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "refund_event_logs")
class RefundEventLog(
    @Column(nullable = false, unique = true)
    val eventId: String,

    @Column(nullable = false)
    val eventType: String,

    @Column(nullable = false)
    val refundId: Long,

    @Column(nullable = false)
    val orderId: Long,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "refund_event_log_steps", joinColumns = [JoinColumn(name = "refund_event_log_id")])
    @Column(name = "step")
    val completedSteps: MutableSet<String> = mutableSetOf()
) : BaseEntity() {

    fun markStepCompleted(step: String) {
        completedSteps.add(step)
    }

    fun isStepCompleted(step: String): Boolean = step in completedSteps

    companion object {
        const val PAYMENT_UPDATED = "PAYMENT_UPDATED"
        const val POINTS_REFUNDED = "POINTS_REFUNDED"
        const val COUPON_RESTORED = "COUPON_RESTORED"
        const val INVENTORY_RESTORED = "INVENTORY_RESTORED"
        const val STATS_UPDATED = "STATS_UPDATED"
        const val ORDER_CANCELLED = "ORDER_CANCELLED"
    }
}
