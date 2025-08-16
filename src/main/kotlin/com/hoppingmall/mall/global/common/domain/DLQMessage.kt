package com.hoppingmall.mall.global.common.domain

import com.hoppingmall.mall.global.common.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "dlq_messages",
    indexes = [
        Index(name = "idx_dlq_topic", columnList = "original_topic"),
        Index(name = "idx_dlq_status", columnList = "status"),
        Index(name = "idx_dlq_created_at", columnList = "created_at")
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
    var notes: String? = null
    
) : BaseEntity() {
    
    /**
     * 재시도 시도
     */
    fun incrementRetry() {
        this.retryCount++
        this.lastRetryAt = System.currentTimeMillis()
        this.status = DLQStatus.RETRYING
    }
    
    /**
     * 처리 완료 처리
     */
    fun markAsProcessed(notes: String? = null) {
        this.status = DLQStatus.PROCESSED
        this.processedAt = System.currentTimeMillis()
        this.notes = notes
    }
    
    /**
     * 실패 처리
     */
    fun markAsFailed(notes: String? = null) {
        this.status = DLQStatus.FAILED
        this.notes = notes
    }
    
    /**
     * 메시지 키 생성 (중복 방지용)
     */
    fun getMessageKey(): String {
        return "${originalTopic}:${originalPartition}:${originalOffset}"
    }
}

enum class DLQStatus {
    PENDING,    // 대기 중
    RETRYING,   // 재시도 중
    PROCESSED,  // 처리 완료
    FAILED      // 최종 실패
}