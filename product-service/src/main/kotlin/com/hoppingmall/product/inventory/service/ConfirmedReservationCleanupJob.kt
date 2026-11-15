package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.port.HttpOrderTerminalCheckAdapter
import com.hoppingmall.product.inventory.port.OrderTerminalCheckPort
import io.micrometer.core.instrument.Counter
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

    private val cancelledOrderSkipCounter = skipCounter(meterRegistry, "cancelled_order")
    private val reasonUnknownSkipCounter = skipCounter(meterRegistry, "reason_unknown")

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
        var skipped = 0
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
            val check = try {
                terminalCheckPort.check(candidates.map { it.second })
            } catch (e: Exception) {
                log.warn("확정 예약 정리 중 주문 끝남 확인 실패, 이번 주기 중단: asked={}, deleted={}, error={}", asked, deleted, e.toString())
                return CleanupResult(asked, deleted, true, skipped)
            }
            val terminalIds = check.terminalReservationIds.toSet()
            val terminal = candidates.filter { it.second in terminalIds }
            val held = held(terminal.map { it.second }, check.cancelledReservationIds)
            skipped += held.size
            val ids = terminal.filter { it.second !in held }.map { it.first }
            if (ids.isNotEmpty()) {
                deleted += deleteConfirmed(ids, cutoff)
            }
            if (candidates.size < properties.batchSize) break
        }
        if (deleted > 0) {
            log.info("확정 예약 정리: asked={}, deleted={}, skipped={}, ageDays={}", asked, deleted, skipped, properties.ageDays)
        }
        return CleanupResult(asked, deleted, false, skipped)
    }

    private fun held(terminal: List<String>, cancelled: List<String>?): Set<String> {
        if (terminal.isEmpty()) return emptySet()
        if (cancelled == null) {
            reasonUnknownSkipCounter.increment(terminal.size.toDouble())
            log.warn(
                "확정 예약 정리: order가 끝난 이유(취소 여부)를 알려 주지 않아 지우지 않음: count={}, sample={}",
                terminal.size,
                terminal.take(LOG_SAMPLE_SIZE)
            )
            return terminal.toSet()
        }
        val cancelledSet = cancelled.toSet()
        val held = terminal.filter { it in cancelledSet }
        if (held.isNotEmpty()) {
            cancelledOrderSkipCounter.increment(held.size.toDouble())
            log.warn(
                "확정 예약 정리: 취소된 주문의 확정 예약은 재고 복원 누락의 증거라 지우지 않음: count={}, sample={}",
                held.size,
                held.take(LOG_SAMPLE_SIZE)
            )
        }
        return held.toSet()
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
        private const val LOG_SAMPLE_SIZE = 10

        private fun skipCounter(meterRegistry: MeterRegistry, reason: String): Counter =
            Counter.builder("inventory.reservation.confirmed_cleanup.skipped")
                .description("Confirmed reservations kept by the cleanup job because the order may have lost its stock restore")
                .tag("reason", reason)
                .register(meterRegistry)

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
