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
    
    /**
     * 토픽별 DLQ 메시지 조회
     */
    fun findByOriginalTopicOrderByCreatedAtDesc(
        originalTopic: String,
        pageable: Pageable
    ): Page<DLQMessage>
    
    /**
     * 상태별 DLQ 메시지 조회
     */
    fun findByStatusOrderByCreatedAtDesc(
        status: DLQStatus,
        pageable: Pageable
    ): Page<DLQMessage>
    
    /**
     * 토픽 및 상태별 DLQ 메시지 조회
     */
    fun findByOriginalTopicAndStatusOrderByCreatedAtDesc(
        originalTopic: String,
        status: DLQStatus,
        pageable: Pageable
    ): Page<DLQMessage>
    
    /**
     * 토픽별 전체 카운트
     */
    fun countByOriginalTopic(originalTopic: String): Long
    
    /**
     * 상태별 전체 카운트
     */
    fun countByStatus(status: DLQStatus): Long
    
    /**
     * 토픽 및 상태별 카운트
     */
    fun countByOriginalTopicAndStatus(originalTopic: String, status: DLQStatus): Long
    
    /**
     * 재시도 가능한 메시지 조회 (PENDING 상태이면서 재시도 횟수가 제한 미만)
     */
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
    
    /**
     * 특정 기간 이전의 처리된 메시지 조회 (정리용)
     */
    @Query("""
        SELECT d FROM DLQMessage d 
        WHERE d.status = :status 
        AND d.processedAt < :beforeTimestamp
    """)
    fun findProcessedMessagesBefore(
        @Param("status") status: DLQStatus,
        @Param("beforeTimestamp") beforeTimestamp: Long
    ): List<DLQMessage>
    
    /**
     * DLQ 통계 조회
     */
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
    
    /**
     * 중복 메시지 확인 (같은 토픽, 파티션, 오프셋)
     */
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