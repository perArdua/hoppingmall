package com.hoppingmall.payment.payment.domain.repository

import com.hoppingmall.payment.payment.domain.CompensationEventLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CompensationEventLogRepository : JpaRepository<CompensationEventLog, Long> {
    fun existsByEventId(eventId: String): Boolean
    fun findByEventId(eventId: String): CompensationEventLog?
}
