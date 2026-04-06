package com.hoppingmall.outbox.repository

import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.domain.OutboxStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {

    @Query(
        """
        SELECT o FROM OutboxEvent o
        WHERE o.processed = false
        AND (o.status = :status OR (o.status = :retryStatus AND o.retryCount < :maxRetries))
        ORDER BY o.createdAt ASC
        LIMIT :limit
    """
    )
    fun findUnprocessedEvents(
        @Param("status") status: OutboxStatus = OutboxStatus.PENDING,
        @Param("retryStatus") retryStatus: OutboxStatus = OutboxStatus.FAILED,
        @Param("maxRetries") maxRetries: Int = 3,
        @Param("limit") limit: Int = 100
    ): List<OutboxEvent>

    @Query(
        """
        SELECT COUNT(o) > 0 FROM OutboxEvent o
        WHERE o.aggregateType = :aggregateType
        AND o.aggregateId = :aggregateId
        AND o.eventType = :eventType
        AND o.eventData = :eventData
        AND o.status IN :statuses
    """
    )
    fun existsDuplicateEvent(
        @Param("aggregateType") aggregateType: String,
        @Param("aggregateId") aggregateId: String,
        @Param("eventType") eventType: String,
        @Param("eventData") eventData: String,
        @Param("statuses") statuses: List<OutboxStatus>
    ): Boolean

    @Query(
        """
        SELECT o FROM OutboxEvent o
        WHERE o.processed = false
        AND o.aggregateId = :aggregateId
        AND o.aggregateType = :aggregateType
        ORDER BY o.createdAt ASC
    """
    )
    fun findByAggregateIdAndType(
        @Param("aggregateId") aggregateId: String,
        @Param("aggregateType") aggregateType: String
    ): List<OutboxEvent>

    @Modifying(clearAutomatically = true)
    @Query(
        """
        DELETE FROM OutboxEvent o
        WHERE o.processed = true
        AND o.processedAt < :cutoffDate
    """
    )
    fun deleteProcessedEventsBefore(@Param("cutoffDate") cutoffDate: LocalDateTime): Int

    @Modifying(clearAutomatically = true)
    @Query(
        """
        UPDATE OutboxEvent o
        SET o.status = :nextStatus,
            o.updatedAt = :updatedAt
        WHERE o.id = :id
        AND o.processed = false
        AND (
            o.status = :pendingStatus
            OR (o.status = :failedStatus AND o.retryCount < :maxRetries)
        )
    """
    )
    fun claimEventForPublish(
        @Param("id") id: Long,
        @Param("nextStatus") nextStatus: OutboxStatus,
        @Param("updatedAt") updatedAt: LocalDateTime,
        @Param("pendingStatus") pendingStatus: OutboxStatus,
        @Param("failedStatus") failedStatus: OutboxStatus,
        @Param("maxRetries") maxRetries: Int
    ): Int

    @Query(
        """
        SELECT COUNT(o) FROM OutboxEvent o
        WHERE o.status = :status
    """
    )
    fun countByStatus(@Param("status") status: OutboxStatus): Long

    @Query(
        """
        SELECT o FROM OutboxEvent o
        WHERE o.processed = false
        AND o.updatedAt < :cutoffDate
        ORDER BY o.createdAt ASC
        LIMIT :limit
    """
    )
    fun findStaleEvents(
        @Param("cutoffDate") cutoffDate: LocalDateTime,
        @Param("limit") limit: Int = 100
    ): List<OutboxEvent>

    @Modifying(clearAutomatically = true)
    @Query(
        """
        UPDATE OutboxEvent o
        SET o.status = :nextStatus,
            o.updatedAt = :updatedAt
        WHERE o.id = :id
        AND o.processed = false
        AND o.updatedAt < :cutoffDate
        AND o.retryCount < :maxRetries
        AND o.status IN :statuses
    """
    )
    fun claimStaleEvent(
        @Param("id") id: Long,
        @Param("nextStatus") nextStatus: OutboxStatus,
        @Param("updatedAt") updatedAt: LocalDateTime,
        @Param("cutoffDate") cutoffDate: LocalDateTime,
        @Param("maxRetries") maxRetries: Int,
        @Param("statuses") statuses: List<OutboxStatus>
    ): Int
}
