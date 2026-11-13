package com.hoppingmall.dlq.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "dlq_messages",
    indexes = [
        Index(name = "idx_dlq_topic", columnList = "original_topic"),
        Index(name = "idx_dlq_status", columnList = "status"),
        Index(name = "idx_dlq_created_at", columnList = "created_at"),
        Index(name = "idx_dlq_next_retry_at", columnList = "status, next_retry_at"),
        Index(name = "idx_dlq_topic_content_hash", columnList = "original_topic, content_hash")
    ]
)
class DLQMessage(
    @Column(name = "original_topic", nullable = false, length = 255)
    val originalTopic: String,

    @Column(name = "original_partition", nullable = false)
    val originalPartition: Int,

    @Column(name = "original_offset", nullable = false)
    val originalOffset: Long,

    @Column(name = "original_key", length = 500)
    val originalKey: String?,

    @Column(name = "original_value", columnDefinition = "TEXT")
    val originalValue: String?,

    @Column(name = "exception_message", columnDefinition = "TEXT")
    var exceptionMessage: String?,

    @Column(name = "error_timestamp", nullable = false)
    var errorTimestamp: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: DLQStatus = DLQStatus.PENDING,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "last_retry_at")
    var lastRetryAt: Long? = null,

    @Column(name = "processed_at")
    var processedAt: Long? = null,

    @Column(name = "notes", columnDefinition = "TEXT")
    var notes: String? = null,

    @Column(name = "next_retry_at")
    var nextRetryAt: Long? = null,

    @Column(name = "archived_at")
    var archivedAt: Long? = null,

    @Column(name = "content_hash", length = 64)
    var contentHash: String? = null,

    @Column(name = "consumer_group", length = 255)
    val consumerGroup: String? = null

) : BaseEntity() {

    fun incrementRetry() {
        this.retryCount++
        this.lastRetryAt = System.currentTimeMillis()
        this.status = DLQStatus.RETRYING
        this.nextRetryAt = calculateNextRetryAt(this.retryCount)
    }

    fun markAsProcessed(notes: String? = null) {
        this.status = DLQStatus.PROCESSED
        this.processedAt = System.currentTimeMillis()
        this.notes = notes
    }

    fun markAsFailed(notes: String? = null) {
        this.status = DLQStatus.FAILED
        this.notes = notes
    }

    fun reopenAfterRepublishFailure(exceptionMessage: String?, errorTimestamp: Long) {
        this.exceptionMessage = exceptionMessage
        this.errorTimestamp = errorTimestamp
        this.processedAt = null
        this.status = DLQStatus.PENDING
        this.nextRetryAt = calculateNextRetryAt(this.retryCount)
        this.notes = "재발행 뒤 다시 실패"
    }

    fun markAsArchived() {
        this.archivedAt = System.currentTimeMillis()
    }

    fun getMessageKey(): String = "${originalTopic}:${originalPartition}:${originalOffset}"

    fun scheduleNextRetry() {
        this.status = DLQStatus.PENDING
        this.nextRetryAt = calculateNextRetryAt(this.retryCount)
    }

    fun isNonRetryableException(): Boolean {
        val message = exceptionMessage ?: return false
        return NON_RETRYABLE_PATTERNS.any { message.contains(it, ignoreCase = true) }
    }

    companion object {
        @Volatile
        var backoffIntervalsMs: LongArray = longArrayOf(
            60_000L,
            300_000L,
            1_800_000L
        )

        private val NON_RETRYABLE_PATTERNS = listOf(
            "DeserializationException",
            "SerializationException",
            "MessageConversionException",
            "IllegalArgumentException",
            "JsonParseException",
            "JsonMappingException",
            "InvalidFormatException",
            "MethodArgumentNotValidException"
        )

        fun calculateNextRetryAt(retryCount: Int): Long {
            val intervals = backoffIntervalsMs
            val index = (retryCount).coerceAtMost(intervals.size - 1)
            return System.currentTimeMillis() + intervals[index]
        }
    }
}
