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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

@DisplayName("끝난 saga 로그 정리 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SagaEventLogCleanupMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var sagaEventLogRepository: SagaEventLogRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun cleanSagaTables() {
        jdbcTemplate.update("DELETE FROM saga_event_log_steps")
        jdbcTemplate.update("DELETE FROM saga_event_logs")
    }

    @Test
    fun 보관_기간이_지난_끝난_saga_로그와_단계_행을_지운다() {
        val completed = saga(daysAgo = 31, steps = listOf(SagaEventLog.LOCAL_COMPLETED, SagaEventLog.REMOTE_COMPLETED))
        val timedOut = saga(daysAgo = 31, steps = listOf(SagaEventLog.LOCAL_COMPLETED), timedOut = true)

        val deleted = cleaner().cleanUp()

        assertThat(deleted).isEqualTo(2)
        assertThat(remainingIds()).doesNotContain(completed, timedOut)
        assertThat(stepCount(completed) + stepCount(timedOut)).isZero()
    }

    @Test
    fun 끝나지_않은_saga_로그와_보관_기간_안의_로그는_남긴다() {
        val localOnly = saga(daysAgo = 31, steps = listOf(SagaEventLog.LOCAL_COMPLETED))
        val noStep = saga(daysAgo = 31, steps = emptyList())
        val recentCompleted = saga(daysAgo = 29, steps = listOf(SagaEventLog.LOCAL_COMPLETED, SagaEventLog.REMOTE_COMPLETED))
        val recentTimedOut = saga(daysAgo = 29, steps = listOf(SagaEventLog.LOCAL_COMPLETED), timedOut = true)

        val deleted = cleaner().cleanUp()

        assertThat(deleted).isZero()
        assertThat(remainingIds()).containsExactlyInAnyOrder(localOnly, noStep, recentCompleted, recentTimedOut)
        assertThat(stepCount(localOnly)).isEqualTo(1)
        assertThat(stepCount(recentCompleted)).isEqualTo(2)
    }

    @Test
    fun 한_배치는_배치_크기까지만_지우고_정리는_남은_배치를_이어서_지운다() {
        repeat(5) { saga(daysAgo = 40, steps = listOf(SagaEventLog.LOCAL_COMPLETED, SagaEventLog.REMOTE_COMPLETED)) }
        val cleaner = cleaner(batchSize = 2)

        val first = cleaner.deleteBatch(LocalDateTime.now().minusDays(RETENTION_DAYS))

        assertThat(first).isEqualTo(2)
        assertThat(remainingIds()).hasSize(3)

        cleaner.run()

        assertThat(remainingIds()).isEmpty()
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM saga_event_log_steps", Long::class.java)).isZero()
    }

    private fun cleaner(batchSize: Int = 1000) =
        SagaEventLogCleanupScheduler(jdbcTemplate, TransactionTemplate(transactionManager), RETENTION_DAYS, batchSize)

    private fun saga(daysAgo: Long, steps: List<String>, timedOut: Boolean = false): Long {
        val log = SagaEventLog(
            eventId = UUID.randomUUID().toString(),
            eventType = "PAYMENT_COMPLETED",
            orderId = 1L,
            createdAt = LocalDateTime.now().minusDays(daysAgo)
        )
        steps.forEach { log.markStepCompleted(it) }
        if (timedOut) log.markAsTimedOut()
        return sagaEventLogRepository.saveAndFlush(log).id!!
    }

    private fun remainingIds(): List<Long> =
        jdbcTemplate.queryForList("SELECT id FROM saga_event_logs", Long::class.java)

    private fun stepCount(sagaId: Long): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM saga_event_log_steps WHERE saga_event_log_id = ?",
            Long::class.java,
            sagaId
        )!!

    companion object {
        private const val RETENTION_DAYS = 30L
    }
}
