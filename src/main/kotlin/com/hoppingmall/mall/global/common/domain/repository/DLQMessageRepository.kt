package com.hoppingmall.mall.global.common.domain.repository

import com.hoppingmall.mall.global.common.domain.DLQMessage
import com.hoppingmall.mall.global.common.domain.DLQStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface DLQMessageRepository : JpaRepository<DLQMessage, Long> {
    
    fun findByOriginalTopicOrderByCreatedAtDesc(
        originalTopic: String,
        pageable: Pageable
    ): Page<DLQMessage>
    
    fun findByStatusOrderByCreatedAtDesc(
        status: DLQStatus,
        pageable: Pageable
    ): Page<DLQMessage>
    
    fun findByOriginalTopicAndStatusOrderByCreatedAtDesc(
        originalTopic: String,
        status: DLQStatus,
        pageable: Pageable
    ): Page<DLQMessage>
    
    fun countByOriginalTopic(originalTopic: String): Long
    
    fun countByStatus(status: DLQStatus): Long
    
    fun countByOriginalTopicAndStatus(originalTopic: String, status: DLQStatus): Long
    
    @Query("""
        SELECT d FROM DLQMessage d 
        WHERE d.status = :status 
        AND d.retryCount < :maxRetryCount 
        ORDER BY d.createdAt ASC
    """)
    fun findRetryableMessages(
        @Param("status") status: DLQStatus,
        @Param("maxRetryCount") maxRetryCount: Int,
        pageable: Pageable
    ): Page<DLQMessage>
    
    @Query("""
        SELECT d FROM DLQMessage d 
        WHERE d.status = :status 
        AND d.processedAt < :beforeTimestamp
    """)
    fun findProcessedMessagesBefore(
        @Param("status") status: DLQStatus,
        @Param("beforeTimestamp") beforeTimestamp: Long
    ): List<DLQMessage>
    
    @Query("""
        SELECT 
            d.originalTopic as topic,
            COUNT(*) as totalCount,
            SUM(CASE WHEN d.status = 'PENDING' THEN 1 ELSE 0 END) as pendingCount,
            SUM(CASE WHEN d.status = 'PROCESSED' THEN 1 ELSE 0 END) as processedCount,
            SUM(CASE WHEN d.status = 'FAILED' THEN 1 ELSE 0 END) as failedCount
        FROM DLQMessage d 
        GROUP BY d.originalTopic
    """)
    fun getDLQStatsByTopic(): List<DLQStatsProjection>
    
    fun existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
        originalTopic: String,
        originalPartition: Int,
        originalOffset: Long
    ): Boolean
}

interface DLQStatsProjection {
    val topic: String
    val totalCount: Long
    val pendingCount: Long
    val processedCount: Long
    val failedCount: Long
}