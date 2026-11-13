package com.hoppingmall.product.inventory.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
@Profile("!test")
class ReservationCleanupScheduler(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${inventory.reservation.cleanup.retention-days:30}") private val retentionDays: Long,
    @Value("\${inventory.reservation.cleanup.batch-size:1000}") private val batchSize: Int
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${inventory.reservation.cleanup.cron:0 15 4 * * ?}")
    fun run() {
        cleanUp()
    }

    fun cleanUp(): Int {
        val cutoff = LocalDateTime.now().minusDays(retentionDays)
        var total = 0
        do {
            val deleted = jdbcTemplate.update(DELETE_FINISHED, cutoff, cutoff, batchSize)
            total += deleted
        } while (deleted == batchSize)
        if (total > 0) {
            log.info("끝난 재고 예약 정리: deleted={}, retentionDays={}", total, retentionDays)
        }
        return total
    }

    companion object {
        private val DELETE_FINISHED = """
            DELETE FROM inventory_reservations
            WHERE status IN ('CANCELLED', 'EXPIRED')
              AND expires_at < ?
              AND updated_at < ?
            LIMIT ?
        """.trimIndent()
    }
}
