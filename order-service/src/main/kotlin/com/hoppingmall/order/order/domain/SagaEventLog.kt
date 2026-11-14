package com.hoppingmall.order.order.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "saga_event_logs",
    indexes = [
        Index(name = "idx_saga_event_id", columnList = "eventId", unique = true),
        Index(name = "idx_saga_timeout_scan", columnList = "timedOut, eventType, createdAt"),
        Index(name = "idx_saga_order_id", columnList = "orderId")
    ]
)
class SagaEventLog(
    @Column(nullable = false, unique = true, length = 100)
    val eventId: String,

    @Column(nullable = false, length = 50)
    val eventType: String,

    @Column(nullable = false)
    val orderId: Long,

    @Column(nullable = true)
    val paymentId: Long? = null,

    @Column(nullable = true, precision = 10, scale = 2)
    val paymentAmount: BigDecimal? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val timeoutAt: LocalDateTime = LocalDateTime.now().plusMinutes(DEFAULT_TIMEOUT_MINUTES),

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "saga_event_log_steps", joinColumns = [JoinColumn(name = "saga_event_log_id")])
    @Column(name = "step")
    val completedSteps: MutableSet<String> = mutableSetOf()
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false)
    var timedOut: Boolean = false

    fun markStepCompleted(step: String) {
        completedSteps.add(step)
    }

    fun isStepCompleted(step: String): Boolean = step in completedSteps

    fun isFullyCompleted(): Boolean = completedSteps.containsAll(setOf(LOCAL_COMPLETED, REMOTE_COMPLETED))

    fun markAsTimedOut() {
        this.timedOut = true
        markStepCompleted(TIMED_OUT)
    }

    fun isTimedOut(): Boolean = timedOut

    fun claimTimeout() {
        markStepCompleted(TIMEOUT_CLAIMED)
    }

    fun isTimeoutClaimed(): Boolean = TIMEOUT_CLAIMED in completedSteps

    companion object {
        const val LOCAL_COMPLETED = "LOCAL_COMPLETED"
        const val REMOTE_COMPLETED = "REMOTE_COMPLETED"
        const val TIMED_OUT = "TIMED_OUT"
        const val TIMEOUT_CLAIMED = "TIMEOUT_CLAIMED"
        const val RESTORE_CONFIRMED = "RESTORE_CONFIRMED"
        const val DEFAULT_TIMEOUT_MINUTES = 5L
    }
}
