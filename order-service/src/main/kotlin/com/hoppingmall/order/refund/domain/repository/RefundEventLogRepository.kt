package com.hoppingmall.order.refund.domain.repository

import com.hoppingmall.order.refund.domain.RefundEventLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RefundEventLogRepository : JpaRepository<RefundEventLog, Long> {
    fun existsByEventId(eventId: String): Boolean
}
