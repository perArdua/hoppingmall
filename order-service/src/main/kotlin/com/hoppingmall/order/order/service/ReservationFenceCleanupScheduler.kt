package com.hoppingmall.order.order.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ReservationFenceCleanupScheduler(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${order.reservation.fence.enabled:false}") private val enabled: Boolean,
    @Value("\${order.reservation.fence.cleanup.retention-hours:24}") private val retentionHours: Long,
    @Value("\${order.reservation.fence.cleanup.batch-size:1000}") private val batchSize: Int
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${order.reservation.fence.cleanup.interval-ms:3600000}")
    fun run() {
        cleanUp()
    }

    fun cleanUp(): Int {
        if (!enabled) return 0
        val deleted = jdbcTemplate.update(DELETE, retentionHours, batchSize)
        if (deleted > 0) {
            log.info("reservation_fence 행 정리: deleted={}, retentionHours={}", deleted, retentionHours)
        }
        return deleted
    }

    companion object {
        private val DELETE = """
            DELETE FROM reservation_fence
            WHERE created_at < NOW(6) - INTERVAL ? HOUR
              AND (
                decision = '${ReservationFence.ABORTED}'
                OR EXISTS (
                  SELECT 1 FROM order_items oi
                  JOIN orders o ON o.id = oi.order_id
                  WHERE oi.reservation_id = reservation_fence.reservation_id
                    AND o.status IN ('CANCELLED', 'DELIVERED', 'REFUNDED')
                )
              )
            LIMIT ?
        """.trimIndent()
    }
}
