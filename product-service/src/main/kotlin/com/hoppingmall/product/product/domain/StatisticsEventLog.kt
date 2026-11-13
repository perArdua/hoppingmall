package com.hoppingmall.product.product.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "statistics_event_logs",
    uniqueConstraints = [UniqueConstraint(columnNames = ["event_id"])],
    indexes = [Index(name = "idx_statistics_event_logs_order_id_event_type", columnList = "orderId, eventType")]
)
class StatisticsEventLog(
    @Column(name = "event_id", nullable = false, unique = true)
    val eventId: String,

    @Column(nullable = false)
    val eventType: String,

    @Column(nullable = false)
    val orderId: Long
) : BaseEntity()
