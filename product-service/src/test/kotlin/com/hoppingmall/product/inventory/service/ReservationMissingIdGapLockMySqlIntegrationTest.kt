package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.exception.ReservationConfirmFailedException
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@DisplayName("없는 예약 id가 섞인 취소·확정의 갭 잠금 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ReservationMissingIdGapLockMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun 없는_예약_id가_섞인_취소가_진행_중이어도_새_예약_INSERT는_기다리지_않는다() {
        seed()

        val outcome = whileOpen(
            work = { inventoryCommandService.cancelReservations(listOf(EXISTING_ID, MISSING_ID)) },
            rollback = false
        )

        assertThat(outcome.lockWaits).withFailMessage(outcome.diagnostics).isZero()
        assertThat(outcome.reservedBeforeRelease).withFailMessage(outcome.diagnostics).isTrue()
        assertThat(status(EXISTING_ID)).isEqualTo("CANCELLED")
        assertThat(stock(PRODUCT_ID)).isEqualTo(10)
        assertThat(stock(OTHER_PRODUCT_ID)).isEqualTo(9)
    }

    @Test
    fun 없는_예약_id가_섞인_확정이_진행_중이어도_새_예약_INSERT는_기다리지_않는다() {
        seed()

        val outcome = whileOpen(
            work = {
                assertThatThrownBy { inventoryCommandService.confirmReservations(listOf(EXISTING_ID, MISSING_ID)) }
                    .isInstanceOf(ReservationConfirmFailedException::class.java)
            },
            rollback = true
        )

        assertThat(outcome.lockWaits).withFailMessage(outcome.diagnostics).isZero()
        assertThat(outcome.reservedBeforeRelease).withFailMessage(outcome.diagnostics).isTrue()
        assertThat(status(EXISTING_ID)).isEqualTo("RESERVED")
        assertThat(stock(OTHER_PRODUCT_ID)).isEqualTo(9)
    }

    private fun seed() {
        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_ID, stockQuantity = 7))
        inventoryRepository.saveAndFlush(Inventory.create(productId = OTHER_PRODUCT_ID, stockQuantity = 10))
        val now = LocalDateTime.now()
        jdbcTemplate.update(
            """
            INSERT INTO inventory_reservations (reservation_id, product_id, quantity, status, expires_at, created_at, updated_at)
            VALUES (?, ?, 3, 'RESERVED', ?, ?, ?)
            """.trimIndent(),
            EXISTING_ID, PRODUCT_ID, now.plusMinutes(10), now, now
        )
    }

    private fun whileOpen(work: () -> Unit, rollback: Boolean): Outcome {
        val opened = CountDownLatch(1)
        val release = CountDownLatch(1)
        val transaction = executor.submit<Unit> {
            TransactionTemplate(transactionManager).executeWithoutResult { status ->
                try {
                    work()
                    opened.countDown()
                    release.await(30, TimeUnit.SECONDS)
                } finally {
                    if (rollback) status.setRollbackOnly()
                }
            }
        }
        try {
            assertThat(opened.await(15, TimeUnit.SECONDS)).isTrue()
            val reserve = executor.submit<String> { inventoryCommandService.reserveStock(OTHER_PRODUCT_ID, 1) }
            val lockWaits = awaitDoneOrLockWait(reserve)
            val reservedBeforeRelease = reserve.isDone
            val diagnostics = "lockWaits=$lockWaits, reserveDone=$reservedBeforeRelease\n" + diagnostics()
            release.countDown()
            transaction.get(30, TimeUnit.SECONDS)
            reserve.get(30, TimeUnit.SECONDS)
            return Outcome(lockWaits, reservedBeforeRelease, diagnostics)
        } finally {
            release.countDown()
        }
    }

    private fun awaitDoneOrLockWait(pending: Future<*>): Long {
        val deadline = System.currentTimeMillis() + WAIT_LIMIT_MS
        while (System.currentTimeMillis() < deadline) {
            val waits = lockWaitCount("inventory_reservations")
            if (waits > 0) return waits
            if (pending.isDone) return 0
            Thread.onSpinWait()
        }
        return lockWaitCount("inventory_reservations")
    }

    private fun diagnostics(): String = listOf(
        "data_locks=" + jdbcTemplate.queryForList(
            "SELECT ENGINE_TRANSACTION_ID, OBJECT_NAME, INDEX_NAME, LOCK_TYPE, LOCK_MODE, LOCK_STATUS, LOCK_DATA FROM performance_schema.data_locks WHERE OBJECT_SCHEMA = DATABASE()"
        ),
        "data_lock_waits=" + jdbcTemplate.queryForList(
            "SELECT REQUESTING_ENGINE_TRANSACTION_ID, BLOCKING_ENGINE_TRANSACTION_ID, REQUESTING_ENGINE_LOCK_ID, BLOCKING_ENGINE_LOCK_ID FROM performance_schema.data_lock_waits"
        )
    ).joinToString("\n")

    private fun status(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    private fun stock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!

    private data class Outcome(val lockWaits: Long, val reservedBeforeRelease: Boolean, val diagnostics: String)

    companion object {
        private const val PRODUCT_ID = 7501L
        private const val OTHER_PRODUCT_ID = 7502L
        private const val EXISTING_ID = "00000000-0000-0000-0000-000000000001"
        private const val MISSING_ID = "ffffffff-ffff-ffff-ffff-ffffffffffff"
        private const val WAIT_LIMIT_MS = 15_000L
    }
}
