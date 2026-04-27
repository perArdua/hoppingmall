package com.hoppingmall.order.order.domain.repository

import com.hoppingmall.order.order.domain.SagaEventLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface SagaEventLogRepository : JpaRepository<SagaEventLog, Long> {
    fun existsByEventId(eventId: String): Boolean
    fun findByEventId(eventId: String): SagaEventLog?

    @Query(
        """
        SELECT s FROM SagaEventLog s
        WHERE s.timedOut = false
        AND s.timeoutAt < :now
        AND NOT EXISTS (
            SELECT 1 FROM s.completedSteps step WHERE step = 'REMOTE_COMPLETED'
        )
        ORDER BY s.timeoutAt ASC
    """
    )
    fun findTimedOutSagas(@Param("now") now: LocalDateTime): List<SagaEventLog>
}
