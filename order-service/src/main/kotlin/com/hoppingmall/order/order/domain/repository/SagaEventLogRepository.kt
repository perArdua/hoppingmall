package com.hoppingmall.order.order.domain.repository

import com.hoppingmall.order.order.domain.SagaEventLog
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface SagaEventLogRepository : JpaRepository<SagaEventLog, Long> {
    fun existsByEventId(eventId: String): Boolean

    @EntityGraph(attributePaths = ["completedSteps"])
    fun findByEventId(eventId: String): SagaEventLog?

    @Query(
        value = """
        SELECT s.* FROM saga_event_logs s
        WHERE s.timed_out = false
        AND s.remote_completed = false
        AND s.event_type = 'PAYMENT_COMPLETED'
        AND s.created_at < :createdBefore
        AND NOT EXISTS (
            SELECT 1 FROM saga_event_log_steps step
            WHERE step.saga_event_log_id = s.id AND step.step = 'REMOTE_COMPLETED'
        )
        ORDER BY s.created_at ASC
        LIMIT $TIMEOUT_SCAN_LIMIT
    """,
        nativeQuery = true
    )
    fun findTimedOutSagas(@Param("createdBefore") createdBefore: LocalDateTime): List<SagaEventLog>

    companion object {
        const val TIMEOUT_SCAN_LIMIT = 100
    }
}
