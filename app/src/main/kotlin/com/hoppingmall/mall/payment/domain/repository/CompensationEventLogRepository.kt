package com.hoppingmall.mall.payment.domain.repository

import com.hoppingmall.mall.payment.domain.CompensationEventLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CompensationEventLogRepository : JpaRepository<CompensationEventLog, Long> {
    fun existsByEventId(eventId: String): Boolean
}
