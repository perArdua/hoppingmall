package com.hoppingmall.payment.payment.domain

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.common.EnumNameConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
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

    @Convert(converter = CompensationEventLogStatusConverter::class)
    @Column(nullable = false, length = 32)
    var status: CompensationEventLogStatus = CompensationEventLogStatus.PENDING
) : BaseEntity() {

    fun complete() {
        this.status = CompensationEventLogStatus.COMPLETED
    }

    fun isCompleted(): Boolean = this.status == CompensationEventLogStatus.COMPLETED
}

class CompensationEventLogStatusConverter : EnumNameConverter<CompensationEventLogStatus>(CompensationEventLogStatus::class.java)
