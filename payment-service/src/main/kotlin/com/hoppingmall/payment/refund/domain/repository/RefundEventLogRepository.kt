package com.hoppingmall.payment.refund.domain.repository

import com.hoppingmall.payment.refund.domain.RefundEventLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RefundEventLogRepository : JpaRepository<RefundEventLog, Long> {
    fun existsByEventId(eventId: String): Boolean

    @Query("SELECT r FROM RefundEventLog r LEFT JOIN FETCH r.completedSteps WHERE r.eventId = :eventId")
    fun findByEventIdWithSteps(eventId: String): RefundEventLog?
}
