package com.hoppingmall.payment.payment.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "payment_event_logs",
    uniqueConstraints = [UniqueConstraint(columnNames = ["transaction_id"])]
)
class PaymentEventLog(
    @Column(name = "transaction_id", nullable = false)
    val transactionId: String,

    @Column(nullable = false)
    val paymentId: Long,

    @Column(nullable = false)
    val orderId: Long
) : BaseEntity()
