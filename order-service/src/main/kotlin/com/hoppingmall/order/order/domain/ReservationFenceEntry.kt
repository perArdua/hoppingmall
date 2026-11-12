package com.hoppingmall.order.order.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "reservation_fence")
class ReservationFenceEntry(
    @Id
    @Column(name = "reservation_id", length = 36, nullable = false)
    val reservationId: String,

    @Column(nullable = false, length = 10)
    val decision: String,

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
