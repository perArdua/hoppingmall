package com.hoppingmall.product.product.domain.repository

import com.hoppingmall.product.product.domain.StatisticsEventLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StatisticsEventLogRepository : JpaRepository<StatisticsEventLog, Long> {
    fun existsByEventId(eventId: String): Boolean
}
