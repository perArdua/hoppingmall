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
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "saga_event_log_steps", joinColumns = [JoinColumn(name = "saga_event_log_id")])
    @Column(name = "step")
    val completedSteps: MutableSet<String> = mutableSetOf()
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    fun markStepCompleted(step: String) {
        completedSteps.add(step)
    }

    fun isStepCompleted(step: String): Boolean = step in completedSteps

    fun isFullyCompleted(): Boolean = completedSteps.containsAll(setOf(LOCAL_COMPLETED, REMOTE_COMPLETED))

    companion object {
        const val LOCAL_COMPLETED = "LOCAL_COMPLETED"
        const val REMOTE_COMPLETED = "REMOTE_COMPLETED"
    }
}
