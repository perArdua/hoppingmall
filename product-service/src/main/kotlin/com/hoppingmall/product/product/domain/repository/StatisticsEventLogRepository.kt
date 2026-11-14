package com.hoppingmall.product.product.domain.repository

import com.hoppingmall.product.product.domain.StatisticsEventLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface StatisticsEventLogRepository : JpaRepository<StatisticsEventLog, Long> {
    fun existsByEventId(eventId: String): Boolean

    fun existsByOrderIdAndEventType(orderId: Long, eventType: String): Boolean

    @Query(
        value = "SELECT id FROM statistics_event_logs WHERE order_id = :orderId AND event_type IN (:eventTypes) FOR UPDATE",
        nativeQuery = true
    )
    fun lockIdsByOrderIdAndEventTypeIn(
        @Param("orderId") orderId: Long,
        @Param("eventTypes") eventTypes: Collection<String>
    ): List<Long>
}
