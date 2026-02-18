package com.hoppingmall.mall.refund.domain.repository

import com.hoppingmall.mall.refund.domain.RefundEventLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RefundEventLogRepository : JpaRepository<RefundEventLog, Long> {
    fun existsByEventId(eventId: String): Boolean
}
