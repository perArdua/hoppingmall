package com.hoppingmall.payment.payment.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "compensation_event_logs",
    uniqueConstraints = [UniqueConstraint(columnNames = ["event_id"])]
)
class CompensationEventLog(
    @Column(name = "event_id", nullable = false, unique = true)
    val eventId: String,

    @Column(nullable = false)
    val compensationType: String,

    @Column(nullable = false)
    val paymentId: Long,

    @Column(nullable = false)
    val orderId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CompensationEventLogStatus = CompensationEventLogStatus.PENDING
) : BaseEntity() {

    fun complete() {
        this.status = CompensationEventLogStatus.COMPLETED
    }

    fun isCompleted(): Boolean = this.status == CompensationEventLogStatus.COMPLETED
}
