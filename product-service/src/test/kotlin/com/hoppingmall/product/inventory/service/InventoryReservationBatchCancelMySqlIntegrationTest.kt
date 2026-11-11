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
import java.util.concurrent.TimeUnit
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

    private fun cancelWhileHolding(reservationId: String, holdingChanges: (Connection) -> Unit): Result<Unit> {
        dataSource.connection.use { holder ->
            holder.autoCommit = false
            holdingChanges(holder)
            val holderTransactionId = transactionIdOf(holder)

            val cancel = executor.submit<Result<Unit>> {
                runCatching { inventoryCommandService.cancelReservations(listOf(reservationId)) }
            }

            awaitReservationLockWait(holderTransactionId)
            holder.commit()
            return cancel.get(30, TimeUnit.SECONDS)
        }
    }

    private fun transactionIdOf(connection: Connection): String =
        connection.prepareStatement("SELECT trx_id FROM information_schema.innodb_trx WHERE trx_mysql_thread_id = CONNECTION_ID()")
            .use { statement -> statement.executeQuery().use { it.next(); it.getString(1) } }

    private fun awaitReservationLockWait(blockingTransactionId: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val waits = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM performance_schema.data_lock_waits w
                JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID
                WHERE w.BLOCKING_ENGINE_TRANSACTION_ID = ?
                AND l.OBJECT_SCHEMA = DATABASE()
                AND l.OBJECT_NAME = 'inventory_reservations'
                """.trimIndent(),
                Long::class.java,
                blockingTransactionId
            )!!
            if (waits > 0) return
            Thread.onSpinWait()
        }
        throw AssertionError("배치 취소가 inventory_reservations 잠금을 기다리지 않았다")
    }

    private fun stock(): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, PRODUCT_ID)!!

    private fun reservationStatus(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    companion object {
        private const val PRODUCT_ID = 2001L

        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            MySqlContainerSupport.register(registry, "product_reservation_batch_cancel")
        }
    }
}
