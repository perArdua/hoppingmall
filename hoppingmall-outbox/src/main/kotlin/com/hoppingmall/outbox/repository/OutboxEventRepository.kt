package com.hoppingmall.outbox.repository

import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.domain.OutboxStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

const val OUTBOX_CLEANUP_BATCH_SIZE = 1000

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
        value = """
        SELECT id FROM outbox_events FORCE INDEX (idx_outbox_processed_created)
        WHERE processed = false
        AND (status = :status OR (status = :retryStatus AND retry_count < :maxRetries))
        ORDER BY created_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
    """,
        nativeQuery = true
    )
    fun lockUnprocessedEventIds(
        @Param("status") status: String,
        @Param("retryStatus") retryStatus: String,
        @Param("maxRetries") maxRetries: Int,
        @Param("limit") limit: Int
    ): List<Long>

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

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
        DELETE FROM outbox_events
        WHERE processed = true
        AND processed_at < :cutoffDate
        LIMIT $OUTBOX_CLEANUP_BATCH_SIZE
    """,
        nativeQuery = true
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

    @Modifying(clearAutomatically = true)
    @Query(
        value = """
        UPDATE outbox_events FORCE INDEX (PRIMARY)
        SET status = :nextStatus,
            updated_at = :updatedAt
        WHERE id IN (:ids)
        AND processed = false
        AND (
            status = :pendingStatus
            OR (status = :failedStatus AND retry_count < :maxRetries)
        )
    """,
        nativeQuery = true
    )
    fun claimEventsForPublish(
        @Param("ids") ids: List<Long>,
        @Param("nextStatus") nextStatus: String,
        @Param("updatedAt") updatedAt: LocalDateTime,
        @Param("pendingStatus") pendingStatus: String,
        @Param("failedStatus") failedStatus: String,
        @Param("maxRetries") maxRetries: Int
    ): Int

    @Modifying(clearAutomatically = true)
    @Query(
        value = """
        UPDATE outbox_events FORCE INDEX (PRIMARY)
        SET processed = true,
            status = :publishedStatus,
            processed_at = :processedAt,
            updated_at = :updatedAt
        WHERE id IN (:ids)
        AND status = :claimedStatus
        AND updated_at = :claimedAt
    """,
        nativeQuery = true
    )
    fun markClaimedEventsProcessed(
        @Param("ids") ids: List<Long>,
        @Param("publishedStatus") publishedStatus: String,
        @Param("processedAt") processedAt: LocalDateTime,
        @Param("updatedAt") updatedAt: LocalDateTime,
        @Param("claimedStatus") claimedStatus: String,
        @Param("claimedAt") claimedAt: LocalDateTime
    ): Int

    @Modifying(clearAutomatically = true)
    @Query(
        """
        UPDATE OutboxEvent o
        SET o.status = :status,
            o.errorMessage = :errorMessage,
            o.retryCount = :retryCount,
            o.processed = :processed,
            o.processedAt = :processedAt,
            o.updatedAt = :updatedAt
        WHERE o.id = :id
        AND o.status = :claimedStatus
        AND o.updatedAt = :claimedAt
    """
    )
    fun markClaimedEventFailed(
        @Param("id") id: Long,
        @Param("status") status: OutboxStatus,
        @Param("errorMessage") errorMessage: String?,
        @Param("retryCount") retryCount: Int,
        @Param("processed") processed: Boolean,
        @Param("processedAt") processedAt: LocalDateTime?,
        @Param("updatedAt") updatedAt: LocalDateTime,
        @Param("claimedStatus") claimedStatus: OutboxStatus,
        @Param("claimedAt") claimedAt: LocalDateTime
    ): Int

    @Modifying(clearAutomatically = true)
    @Query(
        """
        UPDATE OutboxEvent o
        SET o.status = :pendingStatus,
            o.updatedAt = :updatedAt
        WHERE o.id IN :ids
        AND o.status = :claimedStatus
        AND o.updatedAt = :claimedAt
    """
    )
    fun releaseClaimedEvents(
        @Param("ids") ids: List<Long>,
        @Param("pendingStatus") pendingStatus: OutboxStatus,
        @Param("updatedAt") updatedAt: LocalDateTime,
        @Param("claimedStatus") claimedStatus: OutboxStatus,
        @Param("claimedAt") claimedAt: LocalDateTime
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
        SELECT COUNT(o) FROM OutboxEvent o
        WHERE o.processed = true
        AND o.status = :failedStatus
    """
    )
    fun countParkedEvents(@Param("failedStatus") failedStatus: OutboxStatus): Long

    @Modifying(clearAutomatically = true)
    @Query(
        """
        UPDATE OutboxEvent o
        SET o.status = :pendingStatus,
            o.retryCount = 0,
            o.processed = false,
            o.processedAt = null,
            o.updatedAt = :updatedAt
        WHERE o.id IN :ids
        AND o.processed = true
        AND o.status = :failedStatus
    """
    )
    fun requeueParkedEvents(
        @Param("ids") ids: List<Long>,
        @Param("pendingStatus") pendingStatus: OutboxStatus,
        @Param("failedStatus") failedStatus: OutboxStatus,
        @Param("updatedAt") updatedAt: LocalDateTime
    ): Int

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
