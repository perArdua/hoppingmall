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

    companion object {
        private const val SCAN_LIMIT = 100
    }
}
