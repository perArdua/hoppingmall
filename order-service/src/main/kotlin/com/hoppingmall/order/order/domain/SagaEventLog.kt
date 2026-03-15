package com.hoppingmall.order.order.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "saga_event_logs",
    indexes = [
        Index(name = "idx_saga_event_id", columnList = "eventId", unique = true)
    ]
)
class SagaEventLog(
    @Column(nullable = false, unique = true, length = 100)
    val eventId: String,

    @Column(nullable = false, length = 50)
    val eventType: String,

    @Column(nullable = false)
    val orderId: Long,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
