package com.hoppingmall.mall.product.domain

import com.hoppingmall.mall.global.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "statistics_event_logs",
    uniqueConstraints = [UniqueConstraint(columnNames = ["event_id"])]
)
class StatisticsEventLog(
    @Column(name = "event_id", nullable = false, unique = true)
    val eventId: String,

    @Column(nullable = false)
    val eventType: String,

    @Column(nullable = false)
    val orderId: Long
) : BaseEntity()
