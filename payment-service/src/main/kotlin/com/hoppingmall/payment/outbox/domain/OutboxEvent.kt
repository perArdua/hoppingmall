package com.hoppingmall.payment.outbox.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "outbox_events",
    indexes = [
        Index(name = "idx_outbox_processed_created", columnList = "processed, createdAt"),
        Index(name = "idx_outbox_aggregate_id", columnList = "aggregateId")
    ]
)
class OutboxEvent(
    @Column(name = "aggregate_type", nullable = false, length = 100)
    val aggregateType: String,

    @Column(name = "aggregate_id", nullable = false, length = 100)
    val aggregateId: String,

    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String,

    @Column(name = "event_data", nullable = false, columnDefinition = "TEXT")
    val eventData: String,

    @Column(name = "topic", nullable = false, length = 100)
    val topic: String,

    @Column(name = "partition_key", length = 100)
    val partitionKey: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OutboxStatus = OutboxStatus.PENDING,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "processed", nullable = false)
    var processed: Boolean = false

    fun markAsProcessed() {
        this.processed = true
        this.status = OutboxStatus.PUBLISHED
        this.processedAt = LocalDateTime.now()
        this.updatedAt = LocalDateTime.now()
    }

    fun markAsFailed(errorMessage: String) {
        this.status = OutboxStatus.FAILED
        this.errorMessage = errorMessage
        this.retryCount++
        this.updatedAt = LocalDateTime.now()
    }

    fun markAsFailedPermanently(errorMessage: String) {
        markAsFailed(errorMessage)
        this.processed = true
        this.processedAt = LocalDateTime.now()
    }

    fun markAsRetrying() {
        this.status = OutboxStatus.RETRYING
        this.updatedAt = LocalDateTime.now()
    }
}

enum class OutboxStatus {
    PENDING,
    RETRYING,
    PUBLISHED,
    FAILED
}
