package com.hoppingmall.payment.refund.domain.repository

import com.hoppingmall.payment.refund.domain.RefundEventLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RefundEventLogRepository : JpaRepository<RefundEventLog, Long> {
    fun existsByEventId(eventId: String): Boolean
    fun findByEventId(eventId: String): RefundEventLog?
}
