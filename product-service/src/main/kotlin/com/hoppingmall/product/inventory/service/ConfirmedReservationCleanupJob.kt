package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.port.HttpOrderTerminalCheckAdapter
import com.hoppingmall.product.inventory.port.OrderTerminalCheckPort
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.LocalDateTime

@Component
@Profile("!test")
class ConfirmedReservationCleanupJob(
    private val jdbcTemplate: JdbcTemplate,
    private val terminalCheckPort: OrderTerminalCheckPort,
    private val properties: ConfirmedReservationCleanupProperties,
    meterRegistry: MeterRegistry
) {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        require(properties.batchSize in 1..HttpOrderTerminalCheckAdapter.MAX_RESERVATION_IDS) {
            "inventory.reservation.confirmed-cleanup.batch-size must be between 1 and ${HttpOrderTerminalCheckAdapter.MAX_RESERVATION_IDS}"
        }
        require(properties.ageDays > 0) { "inventory.reservation.confirmed-cleanup.age-days must be positive" }
        require(properties.maxBatches > 0) { "inventory.reservation.confirmed-cleanup.max-batches must be positive" }
    }

    @Scheduled(cron = "#{@confirmedReservationCleanupProperties.cron}")
    fun run() {
        cleanUp(LocalDateTime.now())
    }

    fun cleanUp(now: LocalDateTime): CleanupResult {
        val cutoff = Timestamp.valueOf(now.minusDays(properties.ageDays))
        var lastId = 0L
        var asked = 0
        var deleted = 0
        var batches = 0
        while (batches < properties.maxBatches) {
            val candidates = jdbcTemplate.query(
                SELECT_CONFIRMED,
                { rs, _ -> rs.getLong("id") to rs.getString("reservation_id") },
                cutoff,
                lastId,
                properties.batchSize
            )
            if (candidates.isEmpty()) break
            batches++
            asked += candidates.size
            lastId = candidates.last().first
            val terminal = try {
                terminalCheckPort.terminalReservationIds(candidates.map { it.second }).toSet()
            } catch (e: Exception) {
                log.warn("확정 예약 정리 중 주문 끝남 확인 실패, 이번 주기 중단: asked={}, deleted={}, error={}", asked, deleted, e.toString())
                return CleanupResult(asked, deleted, true)
            }
            val ids = candidates.filter { it.second in terminal }.map { it.first }
            if (ids.isNotEmpty()) {
                deleted += deleteConfirmed(ids, cutoff)
            }
            if (candidates.size < properties.batchSize) break
        }
        if (deleted > 0) {
            log.info("확정 예약 정리: asked={}, deleted={}, ageDays={}", asked, deleted, properties.ageDays)
        }
        return CleanupResult(asked, deleted, false)
    }

    private fun deleteConfirmed(ids: List<Long>, cutoff: Timestamp): Int {
        val placeholders = ids.joinToString(",") { "?" }
        return jdbcTemplate.update(
            "DELETE FROM inventory_reservations WHERE id IN ($placeholders) AND status = 'CONFIRMED' AND updated_at < ?",
            *ids.toTypedArray(),
            cutoff
        )
    }

    data class CleanupResult(val asked: Int, val deleted: Int, val failed: Boolean, val skipped: Int = 0)

    companion object {
        private val SELECT_CONFIRMED = """
            SELECT id, reservation_id FROM inventory_reservations
            WHERE status = 'CONFIRMED'
              AND updated_at < ?
              AND id > ?
            ORDER BY id
            LIMIT ?
        """.trimIndent()
    }
}
