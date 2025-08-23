package com.hoppingmall.mall.global.common.repository

import com.hoppingmall.mall.global.common.domain.OutboxEvent
import com.hoppingmall.mall.global.common.domain.OutboxStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {
    
    @Query("""
        SELECT o FROM OutboxEvent o 
        WHERE o.processed = false 
        AND (o.status = :status OR (o.status = :retryStatus AND o.retryCount < :maxRetries))
        ORDER BY o.createdAt ASC
        LIMIT :limit
    """)
    fun findUnprocessedEvents(
        @Param("status") status: OutboxStatus = OutboxStatus.PENDING,
        @Param("retryStatus") retryStatus: OutboxStatus = OutboxStatus.FAILED,
        @Param("maxRetries") maxRetries: Int = 3,
        @Param("limit") limit: Int = 100
    ): List<OutboxEvent>
    
    @Query("""
        SELECT o FROM OutboxEvent o 
        WHERE o.processed = false 
        AND o.aggregateId = :aggregateId 
        AND o.aggregateType = :aggregateType
        ORDER BY o.createdAt ASC
    """)
    fun findByAggregateIdAndType(
        @Param("aggregateId") aggregateId: String,
        @Param("aggregateType") aggregateType: String
    ): List<OutboxEvent>
    
    @Modifying
    @Query("""
        DELETE FROM OutboxEvent o 
        WHERE o.processed = true 
        AND o.processedAt < :cutoffDate
    """)
    fun deleteProcessedEventsBefore(@Param("cutoffDate") cutoffDate: LocalDateTime): Int
    
    @Query("""
        SELECT COUNT(o) FROM OutboxEvent o 
        WHERE o.status = :status
    """)
    fun countByStatus(@Param("status") status: OutboxStatus): Long
    
    @Query("""
        SELECT o FROM OutboxEvent o 
        WHERE o.processed = false 
        AND o.createdAt < :cutoffDate
        ORDER BY o.createdAt ASC
        LIMIT :limit
    """)
    fun findStaleEvents(
        @Param("cutoffDate") cutoffDate: LocalDateTime,
        @Param("limit") limit: Int = 100
    ): List<OutboxEvent>
}