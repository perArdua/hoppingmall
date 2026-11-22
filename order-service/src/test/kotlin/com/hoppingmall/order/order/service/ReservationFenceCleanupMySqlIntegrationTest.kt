package com.hoppingmall.order.order.service

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.support.MySqlContainerSupport
import com.hoppingmall.order.support.MySqlJpaTestSupport
import com.hoppingmall.order.support.seedFenceRows
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@DisplayName("reservation_fence 행 정리 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ReservationFenceCleanupMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Value("\${spring.datasource.url}")
    private lateinit var jdbcUrl: String

    @Test
    fun 대상_30000행은_한_번에_모두_지우고_끝나지_않은_주문의_행은_남긴다() {
        val unfinished = jdbcTemplate.seedFenceRows(UNFINISHED_ROWS, "LINKED", listOf("PAID", "SHIPPED", "PAYING", "CANCEL_FAILED"), hoursAgo = 72)
        jdbcTemplate.seedFenceRows(10_000, "ABORTED", emptyList(), hoursAgo = 48)
        jdbcTemplate.seedFenceRows(20_000, "LINKED", listOf("CANCELLED", "DELIVERED", "REFUNDED"), hoursAgo = 30)

        val deleted = cleaner(enabled = true).cleanUp()

        assertThat(deleted).isEqualTo(30_000)
        assertThat(remaining()).containsExactlyInAnyOrderElementsOf(unfinished)
    }

    @Test
    fun 삭제는_기본_키_레코드만_잠그고_주문_표를_잠그지_않아_주문_생성과_상태_변경을_막지_않는다() {
        fence("LINKED", hoursAgo = 48, orderStatus = "DELIVERED", rid = "00000000-0000-4000-8000-000000000000")
        val held = fence("ABORTED", hoursAgo = 25, rid = "ffffffff-ffff-4fff-8fff-ffffffffffff")
        val orderId = jdbcTemplate.queryForObject("SELECT id FROM orders", Long::class.java)!!

        newConnection().use { holder ->
            holder.autoCommit = false
            try {
                holder.execute("SELECT reservation_id FROM reservation_fence WHERE reservation_id = '$held' FOR UPDATE")
                val enteredAt = AtomicLong()
                val cleanup = executor.submit(Callable {
                    enteredAt.set(System.currentTimeMillis())
                    cleaner(enabled = true).cleanUp()
                })
                awaitLockWait(cleanup, enteredAt, "fence 정리", "reservation_fence")

                assertThat(recordLocks()).allSatisfy { lock ->
                    assertThat(lock).startsWith("reservation_fence/PRIMARY/").endsWith("X,REC_NOT_GAP")
                }
                newConnection().use { order ->
                    order.execute("SET SESSION innodb_lock_wait_timeout = 2")
                    order.execute("INSERT INTO reservation_fence (reservation_id, decision, created_at) VALUES ('$NEW_RESERVATION', 'LINKED', NOW(6))")
                    order.execute("UPDATE orders SET status = 'REFUNDED' WHERE id = $orderId")
                }

                holder.commit()
                assertThat(cleanup.get(30, TimeUnit.SECONDS)).isEqualTo(2)
            } finally {
                holder.rollback()
            }
        }

        assertThat(remaining()).containsExactly(NEW_RESERVATION)
    }

    @Test
    fun 지우기를_기다리는_사이_같은_예약_id로_새로_쓰인_결정은_남긴다() {
        val rid = fence("ABORTED", hoursAgo = 25)

        newConnection().use { holder ->
            holder.autoCommit = false
            try {
                holder.execute("UPDATE reservation_fence SET decision = 'LINKED', created_at = NOW(6) WHERE reservation_id = '$rid'")
                val enteredAt = AtomicLong()
                val cleanup = executor.submit(Callable {
                    enteredAt.set(System.currentTimeMillis())
                    cleaner(enabled = true).cleanUp()
                })
                awaitLockWait(cleanup, enteredAt, "fence 정리", "reservation_fence")
                holder.commit()

                assertThat(cleanup.get(30, TimeUnit.SECONDS)).isZero()
            } finally {
                holder.rollback()
            }
        }

        assertThat(remaining()).containsExactly(rid)
    }

    @Test
    fun 주문_상품_통계가_낡아도_행마다_주문_상품_전체를_훑지_않는다() {
        jdbcTemplate.execute("ALTER TABLE order_items STATS_AUTO_RECALC = 0")
        try {
            val rows = jdbcTemplate.seedFenceRows(STALE_STATS_ROWS, "LINKED", STALE_STATS_STATUSES, hoursAgo = 48)
            val unfinished = rows.filterIndexed { index, _ -> STALE_STATS_STATUSES[index % STALE_STATS_STATUSES.size] == "PAID" }
            makeOrderItemStatsStale()
            val scannedBefore = scannedRows()

            val deleted = cleaner(enabled = true).cleanUp()

            assertThat(scannedRows() - scannedBefore).isLessThan(STALE_STATS_ROWS * SCANS_PER_ROW_LIMIT)
            assertThat(deleted).isEqualTo(rows.size - unfinished.size)
            assertThat(remaining()).containsExactlyInAnyOrderElementsOf(unfinished)
        } finally {
            jdbcTemplate.execute("ALTER TABLE order_items STATS_AUTO_RECALC = DEFAULT")
            jdbcTemplate.execute("ANALYZE TABLE order_items")
        }
    }

    @Test
    fun 보관_기간이_지난_만료_결정과_끝난_주문의_연결_결정을_지운다() {
        val aborted = fence("ABORTED", hoursAgo = 25)
        val cancelled = fence("LINKED", hoursAgo = 25, orderStatus = "CANCELLED")
        val delivered = fence("LINKED", hoursAgo = 25, orderStatus = "DELIVERED")

        val deleted = cleaner(enabled = true).cleanUp()

        assertThat(deleted).isEqualTo(3)
        assertThat(remaining()).doesNotContain(aborted, cancelled, delivered)
    }

    @Test
    fun 끝나지_않은_주문의_연결_결정과_보관_기간_안의_행은_남긴다() {
        val paying = fence("LINKED", hoursAgo = 25, orderStatus = "PAYING")
        val paid = fence("LINKED", hoursAgo = 25, orderStatus = "PAID")
        val shipped = fence("LINKED", hoursAgo = 25, orderStatus = "SHIPPED")
        val cancelFailed = fence("LINKED", hoursAgo = 25, orderStatus = "CANCEL_FAILED")
        val recentAborted = fence("ABORTED", hoursAgo = 23)
        val recentCancelled = fence("LINKED", hoursAgo = 23, orderStatus = "CANCELLED")

        val deleted = cleaner(enabled = true).cleanUp()

        assertThat(deleted).isZero()
        assertThat(remaining()).containsExactlyInAnyOrder(paying, paid, shipped, cancelFailed, recentAborted, recentCancelled)
    }

    @Test
    fun 묶음_크기보다_많아도_한_번에_남김없이_지운다() {
        repeat(5) { fence("ABORTED", hoursAgo = 30) }

        val deleted = cleaner(enabled = true, batchSize = 2).cleanUp()

        assertThat(deleted).isEqualTo(5)
        assertThat(remaining()).isEmpty()
    }

    @Test
    fun 시간_한도에_걸리면_한_묶음만_지우고_다음_주기에_이어서_모두_지운다() {
        jdbcTemplate.seedFenceRows(30_000, "ABORTED", emptyList(), hoursAgo = 48)
        val cleaner = cleaner(enabled = true, maxRunMs = 0)

        val first = cleaner.cleanUp()
        ReflectionTestUtils.setField(cleaner, "maxRunMs", MAX_RUN_MS)
        val second = cleaner.cleanUp()

        assertThat(first).isEqualTo(1_000)
        assertThat(second).isEqualTo(29_000)
        assertThat(remaining()).isEmpty()
    }

    @Test
    fun 시간_한도가_매번_걸려도_끝나지_않은_행이_앞을_막지_않고_끝난_행까지_간다() {
        val unfinished = jdbcTemplate.seedFenceRows(UNFINISHED_ROWS, "LINKED", listOf("PAID"), hoursAgo = 72)
        jdbcTemplate.seedFenceRows(1_500, "ABORTED", emptyList(), hoursAgo = 48)
        val cleaner = cleaner(enabled = true, maxRunMs = 0)

        val deleted = List(5) { cleaner.cleanUp() }

        assertThat(deleted).containsExactly(0, 0, 500, 1_000, 0)
        assertThat(remaining()).containsExactlyInAnyOrderElementsOf(unfinished)
    }

    @Test
    fun 이어서_돌_때_지나온_앞쪽에서_새로_끝난_주문의_행도_같은_주기에_지운다() {
        val becomesDelivered = fence("LINKED", hoursAgo = 72, orderStatus = "SHIPPED")
        repeat(3) { fence("ABORTED", hoursAgo = 30) }
        val cleaner = cleaner(enabled = true, batchSize = 2, maxRunMs = 0)
        cleaner.cleanUp()
        jdbcTemplate.update("UPDATE orders SET status = 'DELIVERED'")
        ReflectionTestUtils.setField(cleaner, "maxRunMs", MAX_RUN_MS)

        cleaner.cleanUp()

        assertThat(remaining()).doesNotContain(becomesDelivered).isEmpty()
    }

    @Test
    fun 묶음마다_따로_커밋한다() {
        val committed = List(2) { fence("ABORTED", hoursAgo = 50) }
        val held = fence("ABORTED", hoursAgo = 25)

        newConnection().use { holder ->
            holder.autoCommit = false
            try {
                holder.execute("SELECT reservation_id FROM reservation_fence WHERE reservation_id = '$held' FOR UPDATE")
                val enteredAt = AtomicLong()
                val cleanup = executor.submit(Callable {
                    enteredAt.set(System.currentTimeMillis())
                    cleaner(enabled = true, batchSize = 2).cleanUp()
                })
                awaitLockWait(cleanup, enteredAt, "fence 정리", "reservation_fence")

                assertThat(remaining()).containsExactly(held).doesNotContainAnyElementsOf(committed)
                holder.commit()
                assertThat(cleanup.get(30, TimeUnit.SECONDS)).isEqualTo(3)
            } finally {
                holder.rollback()
            }
        }
    }

    @Test
    fun 두_인스턴스가_같이_돌아도_교착_없이_한_번씩만_지운다() {
        jdbcTemplate.seedFenceRows(10_000, "ABORTED", emptyList(), hoursAgo = 48)
        val cleaners = List(2) { cleaner(enabled = true) }

        val deleted = cleaners.map { executor.submit(Callable { it.cleanUp() }) }.map { it.get(60, TimeUnit.SECONDS) }

        assertThat(deleted.sum()).isEqualTo(10_000)
        assertThat(remaining()).isEmpty()
    }

    @Test
    fun 묶음_크기가_0이면_만들_때_거절한다() {
        assertThatThrownBy { cleaner(enabled = true, batchSize = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("batch-size")
    }

    @Test
    fun fence가_꺼져_있으면_아무것도_지우지_않는다() {
        val aborted = fence("ABORTED", hoursAgo = 25)
        val cancelled = fence("LINKED", hoursAgo = 25, orderStatus = "CANCELLED")

        val deleted = cleaner(enabled = false).cleanUp()

        assertThat(deleted).isZero()
        assertThat(remaining()).containsExactlyInAnyOrder(aborted, cancelled)
    }

    private fun cleaner(enabled: Boolean, batchSize: Int = 1000, maxRunMs: Long = MAX_RUN_MS) =
        ReservationFenceCleanupScheduler(jdbcTemplate, transactionManager, enabled, RETENTION_HOURS, batchSize, maxRunMs)

    private fun newConnection(): Connection =
        DriverManager.getConnection(jdbcUrl, MySqlContainerSupport.username, MySqlContainerSupport.password)

    private fun Connection.execute(sql: String) {
        createStatement().use { it.execute(sql) }
    }

    private fun makeOrderItemStatsStale() {
        jdbcTemplate.update(
            "UPDATE mysql.innodb_table_stats SET n_rows = ? WHERE database_name = DATABASE() AND table_name = 'order_items'",
            STALE_STATS_ROWS
        )
        jdbcTemplate.update(
            """
            UPDATE mysql.innodb_index_stats SET stat_value = 1
            WHERE database_name = DATABASE() AND table_name = 'order_items'
              AND index_name = 'idx_order_items_reservation_id' AND stat_name LIKE 'n_diff_pfx%'
            """.trimIndent()
        )
        jdbcTemplate.execute("FLUSH TABLES order_items")
    }

    private fun scannedRows(): Long =
        jdbcTemplate.queryForObject(
            "SELECT CAST(VARIABLE_VALUE AS UNSIGNED) FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Handler_read_rnd_next'",
            Long::class.java
        )!!

    private fun recordLocks(): List<String> =
        jdbcTemplate.queryForList(
            """
            SELECT CONCAT(OBJECT_NAME, '/', INDEX_NAME, '/', LOCK_MODE) FROM performance_schema.data_locks
            WHERE OBJECT_SCHEMA = DATABASE() AND LOCK_TYPE = 'RECORD'
            """.trimIndent(),
            String::class.java
        )

    private fun fence(
        decision: String,
        hoursAgo: Long,
        orderStatus: String? = null,
        rid: String = UUID.randomUUID().toString()
    ): String {
        jdbcTemplate.update(
            "INSERT INTO reservation_fence (reservation_id, decision, created_at) VALUES (?, ?, NOW(6) - INTERVAL ? HOUR)",
            rid, decision, hoursAgo
        )
        if (orderStatus != null) {
            val orderId = orderRepository.saveAndFlush(Order.create(buyerId = 1L, totalAmount = BigDecimal("1000"))).id!!
            orderItemRepository.saveAndFlush(
                OrderItem.create(
                    orderId = orderId, sellerId = 5L, productId = 100L,
                    productName = "상품", productPrice = BigDecimal("1000"),
                    quantity = 1, reservationId = rid
                )
            )
            jdbcTemplate.update("UPDATE orders SET status = ? WHERE id = ?", orderStatus, orderId)
        }
        return rid
    }

    private fun remaining(): List<String> =
        jdbcTemplate.queryForList("SELECT reservation_id FROM reservation_fence", String::class.java)

    companion object {
        private const val RETENTION_HOURS = 24L
        private const val MAX_RUN_MS = 120_000L
        private const val UNFINISHED_ROWS = 2_500
        private val STALE_STATS_STATUSES = listOf("CANCELLED", "DELIVERED", "REFUNDED", "PAID")
        private const val STALE_STATS_ROWS = 3_000
        private const val SCANS_PER_ROW_LIMIT = 50L
        private const val NEW_RESERVATION = "88888888-8888-4888-8888-888888888888"
    }
}

@DisplayName("reservation_fence 행 정리 켜짐 조건 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(ReservationFenceCleanupScheduler::class)
@TestPropertySource(properties = ["order.reservation.fence.enabled=false"])
class ReservationFenceCleanupClaimByCommitMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var cleaner: ReservationFenceCleanupScheduler

    @Test
    fun fence_플래그가_꺼져도_기본으로_켜진_커밋_선점_때문에_보관_기간이_지난_취소_결정을_지운다() {
        jdbcTemplate.update(
            "INSERT INTO reservation_fence (reservation_id, decision, created_at) VALUES (?, 'ABORTED', NOW(6) - INTERVAL 25 HOUR)",
            UUID.randomUUID().toString()
        )

        assertThat(cleaner.cleanUp()).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservation_fence", Int::class.java)).isZero()
    }
}
