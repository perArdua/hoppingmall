package com.hoppingmall.order.order.service

import com.hoppingmall.order.order.domain.SagaEventLog
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

@DisplayName("saga 시간 초과 조회 건수 상한 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SagaTimeoutScanLimitMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var sagaEventLogRepository: SagaEventLogRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM saga_event_log_steps")
        jdbcTemplate.update("DELETE FROM saga_event_logs")
    }

    @Test
    fun 시간_초과_saga가_상한보다_많으면_오래된_것부터_상한만큼만_읽는다() {
        val base = LocalDateTime.now().minusHours(1)
        val sagas = (0 until SCAN_LIMIT + 1).map { index ->
            val createdAt = base.plusSeconds(index.toLong())
            SagaEventLog(
                eventId = "payment-completed-scan-$index",
                eventType = "PAYMENT_COMPLETED",
                orderId = index + 1L,
                paymentId = index + 1000L,
                createdAt = createdAt,
                timeoutAt = createdAt.plusMinutes(SagaEventLog.DEFAULT_TIMEOUT_MINUTES)
            ).apply { markStepCompleted(SagaEventLog.LOCAL_COMPLETED) }
        }
        sagaEventLogRepository.saveAll(sagas)

        val timedOut = sagaEventLogRepository.findTimedOutSagas(LocalDateTime.now().minusMinutes(5))

        assertThat(timedOut).hasSize(SCAN_LIMIT)
        assertThat(timedOut.map { it.eventId })
            .containsExactlyElementsOf((0 until SCAN_LIMIT).map { "payment-completed-scan-$it" })
    }

    @Test
    fun 시간_초과_조회는_다른_종류와_시간_초과_처리된_saga를_건너뛰는_전용_인덱스를_탄다() {
        val base = LocalDateTime.now().minusDays(2)
        val rows = mutableListOf<Array<Any>>()
        var seq = 0
        fun add(eventType: String, timedOut: Boolean) {
            val createdAt = base.plusSeconds(seq.toLong())
            rows.add(arrayOf("plan-$seq", eventType, seq + 1L, createdAt, createdAt.plusMinutes(SagaEventLog.DEFAULT_TIMEOUT_MINUTES), timedOut))
            seq++
        }
        repeat(OTHER_TYPE_ROWS) { add(OTHER_EVENT_TYPES[it % OTHER_EVENT_TYPES.size], false) }
        repeat(TIMED_OUT_ROWS) { add("PAYMENT_COMPLETED", true) }
        repeat(PENDING_ROWS) { add("PAYMENT_COMPLETED", false) }
        jdbcTemplate.batchUpdate(
            "INSERT INTO saga_event_logs (event_id, event_type, order_id, created_at, timeout_at, timed_out) VALUES (?, ?, ?, ?, ?, ?)",
            rows
        )
        jdbcTemplate.execute("ANALYZE TABLE saga_event_logs")

        val plan = jdbcTemplate.queryForList(
            """
            EXPLAIN SELECT s.* FROM saga_event_logs s
            WHERE s.timed_out = false
            AND s.event_type = 'PAYMENT_COMPLETED'
            AND s.created_at < NOW()
            AND NOT EXISTS (
                SELECT 1 FROM saga_event_log_steps step
                WHERE step.saga_event_log_id = s.id AND step.step = 'REMOTE_COMPLETED'
            )
            ORDER BY s.created_at ASC
            LIMIT 100
            """.trimIndent()
        ).first { it["table"] == "s" }

        assertThat(plan["key"]).describedAs(plan.toString()).isEqualTo("idx_saga_timeout_scan")
        assertThat(plan["type"]).describedAs(plan.toString()).isNotEqualTo("ALL")
    }

    companion object {
        private const val SCAN_LIMIT = 100
        private const val OTHER_TYPE_ROWS = 3000
        private const val TIMED_OUT_ROWS = 500
        private const val PENDING_ROWS = 300
        private val OTHER_EVENT_TYPES = listOf("PAYMENT_COMPENSATION", "PAYMENT_CANCELLATION_COMPLETED", "PAYMENT_CANCELLATION_FAILED")
    }
}
