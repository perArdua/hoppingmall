package com.hoppingmall.order.refund.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "refund_event_logs",
    uniqueConstraints = [UniqueConstraint(columnNames = ["event_id"])]
)
class RefundEventLog(
    @Column(name = "event_id", nullable = false, unique = true)
    val eventId: String,

    @Column(nullable = false)
    val eventType: String,

    @Column(nullable = false)
    val refundId: Long,

    @Column(nullable = false)
    val orderId: Long
) : BaseEntity()
