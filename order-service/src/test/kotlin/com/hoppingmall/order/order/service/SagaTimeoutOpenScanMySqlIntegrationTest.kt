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
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

@DisplayName("saga 시간 초과 조회가 끝나지 않은 saga만 읽는지 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SagaTimeoutOpenScanMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var sagaEventLogRepository: SagaEventLogRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM saga_event_log_steps")
        jdbcTemplate.update("DELETE FROM saga_event_logs")
    }

    @Test
    fun 끝난_결제_완료_saga가_많아도_시간_초과_조회는_진행_중인_saga만_읽는다() {
        val base = LocalDateTime.now().minusDays(1)
        val finished = (0 until FINISHED_ROWS).map { index ->
            paymentCompletedSaga("finished-$index", index, base.plusSeconds(index.toLong()))
                .apply {
                    markStepCompleted(SagaEventLog.LOCAL_COMPLETED)
                    markStepCompleted(SagaEventLog.REMOTE_COMPLETED)
                }
        }
        val open = (0 until OPEN_ROWS).map { index ->
            paymentCompletedSaga("open-$index", FINISHED_ROWS + index, base.plusSeconds((FINISHED_ROWS + index).toLong()))
                .apply { markStepCompleted(SagaEventLog.LOCAL_COMPLETED) }
        }
        finished.chunked(500).forEach { sagaEventLogRepository.saveAll(it) }
        sagaEventLogRepository.saveAll(open)
        jdbcTemplate.execute("ANALYZE TABLE saga_event_logs")

        val plan = jdbcTemplate.queryForList("EXPLAIN " + timeoutScanSql().replace(":createdBefore", "NOW()"))
            .first { it["table"] == "s" }
        val found = sagaEventLogRepository.findTimedOutSagas(LocalDateTime.now().minusMinutes(5))

        assertThat(found.map { it.eventId }).containsExactlyElementsOf((0 until OPEN_ROWS).map { "open-$it" })
        assertThat((plan["rows"] as Number).toLong()).describedAs(plan.toString()).isLessThanOrEqualTo(OPEN_ROWS * 2L)
    }

    private fun paymentCompletedSaga(eventId: String, index: Int, createdAt: LocalDateTime) =
        SagaEventLog(
            eventId = eventId,
            eventType = "PAYMENT_COMPLETED",
            orderId = index + 1L,
            paymentId = index + 1000L,
            createdAt = createdAt,
            timeoutAt = createdAt.plusMinutes(SagaEventLog.DEFAULT_TIMEOUT_MINUTES)
        )

    private fun timeoutScanSql(): String =
        SagaEventLogRepository::class.java
            .getMethod("findTimedOutSagas", LocalDateTime::class.java)
            .getAnnotation(Query::class.java)
            .value

    companion object {
        private const val FINISHED_ROWS = 3000
        private const val OPEN_ROWS = 10
    }
}
