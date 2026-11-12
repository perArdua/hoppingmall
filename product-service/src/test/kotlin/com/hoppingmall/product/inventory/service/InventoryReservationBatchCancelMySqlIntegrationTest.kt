package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.support.MySqlContainerSupport
import com.hoppingmall.product.support.TestCacheConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.sql.Connection
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource

@DisplayName("재고 예약 배치 취소 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@ActiveProfiles("test")
@Import(TestCacheConfig::class)
class InventoryReservationBatchCancelMySqlIntegrationTest {

    @Autowired
    private lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var dataSource: DataSource

    private val executor = Executors.newSingleThreadExecutor()

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM inventory_reservations")
        jdbcTemplate.update("DELETE FROM inventories")
    }

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun 배치_취소가_만료_처리와_겹치면_만료가_복원한_수량을_다시_복원하지_않는다() {
        val reservationId = reserveThree()

        val result = cancelWhileHolding(reservationId) { connection ->
            connection.prepareStatement(
                "UPDATE inventory_reservations SET status = 'EXPIRED' WHERE reservation_id = ? AND status = 'RESERVED'"
            ).use { it.setString(1, reservationId); assertThat(it.executeUpdate()).isEqualTo(1) }
            connection.prepareStatement(
                "UPDATE inventories SET stock_quantity = stock_quantity + 3 WHERE product_id = ?"
            ).use { it.setLong(1, PRODUCT_ID); assertThat(it.executeUpdate()).isEqualTo(1) }
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(stock()).isEqualTo(10)
        assertThat(reservationStatus(reservationId)).isEqualTo("EXPIRED")
    }

    @Test
    fun 배치_취소가_예약_확정과_겹치면_확정된_예약의_수량을_복원하지_않는다() {
        val reservationId = reserveThree()

        val result = cancelWhileHolding(reservationId) { connection ->
            connection.prepareStatement(
                "UPDATE inventory_reservations SET status = 'CONFIRMED' WHERE reservation_id = ? AND status = 'RESERVED'"
            ).use { it.setString(1, reservationId); assertThat(it.executeUpdate()).isEqualTo(1) }
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(stock()).isEqualTo(7)
        assertThat(reservationStatus(reservationId)).isEqualTo("CONFIRMED")
    }

    private fun reserveThree(): String {
        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_ID, stockQuantity = 10))
        val reservationId = inventoryCommandService.reserveStock(PRODUCT_ID, 3)
        assertThat(stock()).isEqualTo(7)
        return reservationId
    }

    private fun cancelWhileHolding(reservationId: String, holdingChanges: (Connection) -> Unit): Result<Unit> =
        runWhileHolding(holdingChanges, "배치 취소", "inventory_reservations") {
            inventoryCommandService.cancelReservations(listOf(reservationId))
        }

    private fun runWhileHolding(
        holdingChanges: (Connection) -> Unit,
        actionName: String,
        waitTable: String,
        action: () -> Unit
    ): Result<Unit> {
        dataSource.connection.use { holder ->
            holder.autoCommit = false
            holdingChanges(holder)

            val enteredAt = AtomicLong(0L)
            val pending = executor.submit<Result<Unit>> {
                enteredAt.set(System.currentTimeMillis())
                runCatching { action() }
            }

            awaitLockWait(pending, enteredAt, actionName, waitTable)
            holder.commit()
            return pending.get(30, TimeUnit.SECONDS)
        }
    }

    private fun awaitLockWait(pending: Future<Result<Unit>>, enteredAt: AtomicLong, actionName: String, waitTable: String) {
        val startedAt = System.currentTimeMillis()
        val deadline = startedAt + LOCK_WAIT_LIMIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (lockWaitCount(waitTable) > 0) return
            if (pending.isDone) {
                throw AssertionError("${actionName}가 잠금에 걸리지 않고 끝났다\n" + lockDiagnostics(pending, enteredAt, startedAt))
            }
            Thread.onSpinWait()
        }
        throw AssertionError("${actionName}의 $waitTable 잠금 대기가 ${LOCK_WAIT_LIMIT_MS}ms 안에 보이지 않았다\n" + lockDiagnostics(pending, enteredAt, startedAt))
    }

    private fun lockWaitCount(waitTable: String): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM performance_schema.data_lock_waits w
            JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID
            WHERE l.OBJECT_SCHEMA = DATABASE()
            AND l.OBJECT_NAME = ?
            """.trimIndent(),
            Long::class.java,
            waitTable
        )!!

    private fun lockDiagnostics(pending: Future<Result<Unit>>, enteredAt: AtomicLong, startedAt: Long): String {
        val now = System.currentTimeMillis()
        val entered = enteredAt.get()
        val outcome = if (pending.isDone) runCatching { pending.get() }.fold({ it.toString() }, { it.toString() }) else "running"
        return listOf(
            "elapsedSincePollStartMs=${now - startedAt}",
            "actionEnteredAt=${if (entered == 0L) "not yet" else "$entered (${entered - startedAt}ms from poll start)"}",
            "future.done=${pending.isDone}, outcome=$outcome",
            "innodb_trx=" + jdbcTemplate.queryForList(
                "SELECT trx_id, trx_state, trx_mysql_thread_id, trx_started, trx_query FROM information_schema.innodb_trx"
            ),
            "data_locks=" + jdbcTemplate.queryForList(
                "SELECT ENGINE_TRANSACTION_ID, OBJECT_SCHEMA, OBJECT_NAME, INDEX_NAME, LOCK_TYPE, LOCK_MODE, LOCK_STATUS, LOCK_DATA FROM performance_schema.data_locks"
            ),
            "data_lock_waits=" + jdbcTemplate.queryForList(
                "SELECT REQUESTING_ENGINE_TRANSACTION_ID, BLOCKING_ENGINE_TRANSACTION_ID, REQUESTING_ENGINE_LOCK_ID, BLOCKING_ENGINE_LOCK_ID FROM performance_schema.data_lock_waits"
            )
        ).joinToString("\n")
    }

    private fun stock(): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, PRODUCT_ID)!!

    private fun reservationStatus(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    companion object {
        private const val PRODUCT_ID = 2001L
        private const val LOCK_WAIT_LIMIT_MS = 15_000L

        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            MySqlContainerSupport.register(registry, "product_reservation_batch_cancel")
        }
    }
}
