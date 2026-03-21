package com.hoppingmall.order.order.domain.repository

import com.hoppingmall.order.order.domain.SagaEventLog
import org.springframework.data.jpa.repository.JpaRepository

interface SagaEventLogRepository : JpaRepository<SagaEventLog, Long> {
    fun existsByEventId(eventId: String): Boolean
    fun findByEventId(eventId: String): SagaEventLog?
}
