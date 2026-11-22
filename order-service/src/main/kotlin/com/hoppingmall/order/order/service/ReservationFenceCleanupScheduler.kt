package com.hoppingmall.order.order.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.LocalDateTime

@Component
class ReservationFenceCleanupScheduler(
    private val jdbcTemplate: JdbcTemplate,
    @Qualifier("transactionManager") transactionManager: PlatformTransactionManager,
    @Value("#{\${order.reservation.fence.enabled:false} or \${idempotency.claim-by-commit:true}}") private val enabled: Boolean,
    @Value("\${order.reservation.fence.cleanup.retention-hours:24}") private val retentionHours: Long,
    @Value("\${order.reservation.fence.cleanup.batch-size:1000}") private val batchSize: Int,
    @Value("\${order.reservation.fence.cleanup.max-run-ms:60000}") private val maxRunMs: Long
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val deleteTransaction = TransactionTemplate(transactionManager).apply {
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    @Volatile
    private var resumeAfter: ScannedRow? = null

    init {
        require(batchSize > 0) { "order.reservation.fence.cleanup.batch-size must be positive: $batchSize" }
    }

    @Scheduled(fixedDelayString = "\${order.reservation.fence.cleanup.interval-ms:3600000}")
    fun run() {
        cleanUp()
    }

    fun cleanUp(): Int {
        if (!enabled) return 0
        val startedAt = System.nanoTime()
        var passesLeft = if (resumeAfter == null) 1 else 2
        var total = 0
        while (passesLeft > 0) {
            val page = scanPage(resumeAfter)
            total += deleteBatch(page.filter { it.removable }.map { it.reservationId })
            val reachedEnd = page.size < batchSize
            resumeAfter = if (reachedEnd) null else page.last()
            if (reachedEnd) passesLeft--
            if (Duration.ofNanos(System.nanoTime() - startedAt).toMillis() >= maxRunMs) break
        }
        if (passesLeft > 0) {
            log.info("reservation_fence 정리가 시간 한도에 걸려 다음 주기에 이어서 함: deleted={}, maxRunMs={}", total, maxRunMs)
        } else if (total > 0) {
            log.info("reservation_fence 행 정리: deleted={}, retentionHours={}", total, retentionHours)
        }
        return total
    }

    private fun scanPage(after: ScannedRow?): List<ScannedRow> =
        if (after == null) {
            jdbcTemplate.query(SCAN_FIRST, ROW_MAPPER, -retentionHours, batchSize)
        } else {
            jdbcTemplate.query(SCAN_NEXT, ROW_MAPPER, -retentionHours, after.createdAt, after.createdAt, after.reservationId, batchSize)
        }

    private fun deleteBatch(reservationIds: List<String>): Int {
        if (reservationIds.isEmpty()) return 0
        val sql = DELETE_BY_IDS.format(reservationIds.joinToString(",") { "?" })
        val args = arrayOf<Any>(*reservationIds.toTypedArray(), -retentionHours)
        return deleteTransaction.execute { jdbcTemplate.update(sql, *args) } ?: 0
    }

    private data class ScannedRow(val reservationId: String, val createdAt: LocalDateTime, val removable: Boolean)

    companion object {
        private val ROW_MAPPER = RowMapper { rs, _ ->
            ScannedRow(rs.getString(1), rs.getObject(2, LocalDateTime::class.java), rs.getInt(3) == 1)
        }

        private val SCAN = """
            SELECT /*+ INDEX(f idx_reservation_fence_created_at) */ f.reservation_id, f.created_at,
              CASE WHEN f.decision = '${ReservationFence.ABORTED}'
                OR EXISTS (
                  SELECT /*+ JOIN_ORDER(oi, o) INDEX(oi idx_order_items_reservation_id) INDEX(o PRIMARY) */ 1
                  FROM order_items oi
                  JOIN orders o ON o.id = oi.order_id
                  WHERE oi.reservation_id = f.reservation_id
                    AND o.status IN ('CANCELLED', 'DELIVERED', 'REFUNDED')
                )
              THEN 1 ELSE 0 END AS removable
            FROM reservation_fence f
            WHERE f.created_at < TIMESTAMPADD(HOUR, ?, NOW(6))
        """.trimIndent()

        private val SCAN_FIRST = "$SCAN ORDER BY f.created_at, f.reservation_id LIMIT ?"

        private val SCAN_NEXT =
            "$SCAN AND f.created_at >= ? AND (f.created_at > ? OR f.reservation_id > ?) ORDER BY f.created_at, f.reservation_id LIMIT ?"

        private val DELETE_BY_IDS = """
            DELETE /*+ INDEX(reservation_fence PRIMARY) */ FROM reservation_fence
            WHERE reservation_id IN (%s)
              AND created_at < TIMESTAMPADD(HOUR, ?, NOW(6))
        """.trimIndent()
    }
}
