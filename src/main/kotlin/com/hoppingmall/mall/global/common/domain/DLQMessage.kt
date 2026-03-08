package com.hoppingmall.mall.global.common.domain

import com.hoppingmall.mall.global.common.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "dlq_messages",
    indexes = [
        Index(name = "idx_dlq_topic", columnList = "original_topic"),
        Index(name = "idx_dlq_status", columnList = "status"),
        Index(name = "idx_dlq_created_at", columnList = "created_at"),
        Index(name = "idx_dlq_next_retry_at", columnList = "status, next_retry_at")
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
    val exceptionMessage: String?,
    
    @Column(name = "error_timestamp", nullable = false)
    val errorTimestamp: Long,
    
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
    var nextRetryAt: Long? = null

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
    
    fun getMessageKey(): String {
        return "${originalTopic}:${originalPartition}:${originalOffset}"
    }

    fun scheduleNextRetry() {
        this.status = DLQStatus.PENDING
        this.nextRetryAt = calculateNextRetryAt(this.retryCount)
    }

    fun isNonRetryableException(): Boolean {
        val message = exceptionMessage ?: return false
        return NON_RETRYABLE_PATTERNS.any { message.contains(it, ignoreCase = true) }
    }

    companion object {
        private val BACKOFF_INTERVALS = longArrayOf(
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
            val index = (retryCount).coerceAtMost(BACKOFF_INTERVALS.size - 1)
            return System.currentTimeMillis() + BACKOFF_INTERVALS[index]
        }
    }
}

enum class DLQStatus {
    PENDING,
    RETRYING,
    PROCESSED,
    FAILED
}