package com.hoppingmall.order.order.service

import com.hoppingmall.order.order.domain.SagaEventLog
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

@Component
class SagaEventLogCleanupScheduler(
    private val jdbcTemplate: JdbcTemplate,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${saga.log.cleanup.retention-days:30}") private val retentionDays: Long,
    @Value("\${saga.log.cleanup.batch-size:1000}") private val batchSize: Int
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${saga.log.cleanup.cron:0 30 3 * * ?}")
    fun run() {
        cleanUp()
    }

    fun cleanUp(): Int {
        val cutoff = LocalDateTime.now().minusDays(retentionDays)
        var total = 0
        do {
            val deleted = transactionTemplate.execute { deleteBatch(cutoff) } ?: 0
            total += deleted
        } while (deleted == batchSize)
        if (total > 0) {
            log.info("끝난 saga 로그 정리: deleted={}, retentionDays={}", total, retentionDays)
        }
        return total
    }

    internal fun deleteBatch(cutoff: LocalDateTime): Int {
        val ids = jdbcTemplate.queryForList(SELECT_FINISHED, Long::class.java, cutoff, batchSize)
        if (ids.isEmpty()) return 0
        val placeholders = ids.joinToString(",") { "?" }
        val args = ids.toTypedArray<Any>()
        jdbcTemplate.update("DELETE FROM saga_event_log_steps WHERE saga_event_log_id IN ($placeholders)", *args)
        return jdbcTemplate.update("DELETE FROM saga_event_logs WHERE id IN ($placeholders)", *args)
    }

    companion object {
        private val SELECT_FINISHED = """
            SELECT s.id FROM saga_event_logs s
            WHERE s.created_at < ?
              AND (
                s.timed_out = TRUE
                OR (
                  EXISTS (
                    SELECT 1 FROM saga_event_log_steps st
                    WHERE st.saga_event_log_id = s.id AND st.step = '${SagaEventLog.LOCAL_COMPLETED}'
                  )
                  AND EXISTS (
                    SELECT 1 FROM saga_event_log_steps st
                    WHERE st.saga_event_log_id = s.id AND st.step = '${SagaEventLog.REMOTE_COMPLETED}'
                  )
                )
              )
            ORDER BY s.id
            LIMIT ?
        """.trimIndent()
    }
}
