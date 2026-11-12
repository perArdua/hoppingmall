package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

abstract class ConfirmedReservationCancelScenarios : MySqlIntegrationTestSupport() {

    @Autowired
    protected lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    protected lateinit var inventoryQueryService: InventoryQueryService

    @Autowired
    protected lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    protected abstract fun seedStock(productId: Long, total: Int)

    protected abstract fun committedStockSql(): String

    @Test
    fun 확정된_예약을_새_입구로_취소하면_CANCELLED가_되고_재고가_예약_전_값으로_돌아온다() {
        seedStock(PRODUCT_ID, TOTAL)
        val reservationId = reserveAndConfirm(3)

        inventoryCommandService.cancelConfirmedReservations(listOf(reservationId))

        assertThat(reservationStatus(reservationId)).isEqualTo("CANCELLED")
        assertThat(stock()).isEqualTo(TOTAL)
    }

    @Test
    fun 같은_요청을_두_번_보내도_재고는_한_번만_돌아온다() {
        seedStock(PRODUCT_ID, TOTAL)
        val reservationId = reserveAndConfirm(3)

        inventoryCommandService.cancelConfirmedReservations(listOf(reservationId))
        inventoryCommandService.cancelConfirmedReservations(listOf(reservationId))

        assertThat(reservationStatus(reservationId)).isEqualTo("CANCELLED")
        assertThat(stock()).isEqualTo(TOTAL)
    }

    @Test
    fun 같은_예약에_새_입구_요청_두_개가_동시에_오면_재고는_한_번만_돌아온다() {
        seedStock(PRODUCT_ID, TOTAL)
        val reservationId = reserveAndConfirm(3)

        val second = runWhileFirstCallHolds(
            "두 번째 확정 예약 취소",
            first = { inventoryCommandService.cancelConfirmedReservations(listOf(reservationId)) },
            second = { inventoryCommandService.cancelConfirmedReservations(listOf(reservationId)) }
        )

        assertThat(second.isSuccess).isTrue()
        assertThat(reservationStatus(reservationId)).isEqualTo("CANCELLED")
        assertThat(stock()).isEqualTo(TOTAL)
    }

    @Test
    fun 같은_상품의_확정된_예약_둘_중_하나만_취소하면_그_수량만_돌아오고_다른_예약은_CONFIRMED로_남는다() {
        seedStock(PRODUCT_ID, TOTAL)
        val cancelled = reserveAndConfirm(3)
        val kept = reserveAndConfirm(2)
        assertThat(stock()).isEqualTo(TOTAL - 5)

        inventoryCommandService.cancelConfirmedReservations(listOf(cancelled))

        assertThat(reservationStatus(cancelled)).isEqualTo("CANCELLED")
        assertThat(reservationStatus(kept)).isEqualTo("CONFIRMED")
        assertThat(stock()).isEqualTo(TOTAL - 2)
    }

    @Test
    fun 새_입구와_기존_배치_취소가_같은_확정된_예약에_동시에_오면_재고는_한_번만_돌아온다() {
        seedStock(PRODUCT_ID, TOTAL)
        val reservationId = reserveAndConfirm(3)

        val existingCancel = runWhileFirstCallHolds(
            "기존 배치 취소",
            first = { inventoryCommandService.cancelConfirmedReservations(listOf(reservationId)) },
            second = { inventoryCommandService.cancelReservations(listOf(reservationId)) }
        )

        assertThat(existingCancel.isSuccess).isTrue()
        assertThat(reservationStatus(reservationId)).isEqualTo("CANCELLED")
        assertThat(stock()).isEqualTo(TOTAL)
    }

    @Test
    fun RESERVED_예약을_새_입구로_보내면_아무것도_바꾸지_않는다() {
        seedStock(PRODUCT_ID, TOTAL)
        val reservationId = inventoryCommandService.reserveStock(PRODUCT_ID, 3)

        inventoryCommandService.cancelConfirmedReservations(listOf(reservationId))

        assertThat(reservationStatus(reservationId)).isEqualTo("RESERVED")
        assertThat(stock()).isEqualTo(TOTAL - 3)
    }

    @Test
    fun 확정되지_않은_예약이_섞여_있으면_확정된_예약만_취소하고_나머지는_오류_없이_그대로_둔다() {
        seedStock(PRODUCT_ID, TOTAL)
        val confirmed = reserveAndConfirm(1)
        val claimed = inventoryCommandService.reserveStock(PRODUCT_ID, 2)
        val expired = inventoryCommandService.reserveStock(PRODUCT_ID, 3)
        val cancelled = inventoryCommandService.reserveStock(PRODUCT_ID, 4)
        setStatus(claimed, "CLAIMED")
        setStatus(expired, "EXPIRED")
        inventoryCommandService.cancelReservations(listOf(cancelled))
        assertThat(stock()).isEqualTo(TOTAL - 6)

        inventoryCommandService.cancelConfirmedReservations(listOf(confirmed, claimed, expired, cancelled, "rsv-unknown"))

        assertThat(reservationStatus(confirmed)).isEqualTo("CANCELLED")
        assertThat(reservationStatus(claimed)).isEqualTo("CLAIMED")
        assertThat(reservationStatus(expired)).isEqualTo("EXPIRED")
        assertThat(reservationStatus(cancelled)).isEqualTo("CANCELLED")
        assertThat(stock()).isEqualTo(TOTAL - 5)
    }

    @Test
    fun 새_입구로_복원하면_재고_조회에_복원된_수량이_보인다() {
        seedStock(PRODUCT_ID, TOTAL)
        val reservationId = reserveAndConfirm(3)
        assertThat(inventoryQueryService.getStock(PRODUCT_ID).stockQuantity).isEqualTo(TOTAL - 3)

        inventoryCommandService.cancelConfirmedReservations(listOf(reservationId))

        assertThat(inventoryQueryService.getStock(PRODUCT_ID).stockQuantity).isEqualTo(TOTAL)
    }

    private fun reserveAndConfirm(quantity: Int): String {
        val reservationId = inventoryCommandService.reserveStock(PRODUCT_ID, quantity)
        assertThat(inventoryCommandService.confirmReservations(listOf(reservationId))).isTrue()
        assertThat(reservationStatus(reservationId)).isEqualTo("CONFIRMED")
        return reservationId
    }

    private fun runWhileFirstCallHolds(actionName: String, first: () -> Unit, second: () -> Unit): Result<Unit> {
        val pending: Future<Result<Unit>> = transactionTemplate.execute {
            first()
            val enteredAt = AtomicLong(0L)
            val future = executor.submit<Result<Unit>> {
                enteredAt.set(System.currentTimeMillis())
                runCatching { second() }
            }
            awaitLockWait(future, enteredAt, actionName, "inventory_reservations")
            future
        }!!
        return pending.get(30, TimeUnit.SECONDS)
    }

    private fun setStatus(reservationId: String, status: String) {
        assertThat(
            jdbcTemplate.update("UPDATE inventory_reservations SET status = ? WHERE reservation_id = ?", status, reservationId)
        ).isEqualTo(1)
    }

    private fun stock(): Int = jdbcTemplate.queryForObject(committedStockSql(), Int::class.java, PRODUCT_ID)!!

    private fun reservationStatus(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    companion object {
        const val PRODUCT_ID = 5371L
        const val TOTAL = 16
    }
}

@DisplayName("single 모드 확정된 예약 취소 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SingleConfirmedReservationCancelMySqlIntegrationTest : ConfirmedReservationCancelScenarios() {

    override fun seedStock(productId: Long, total: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = total))
    }

    override fun committedStockSql(): String = "SELECT stock_quantity FROM inventories WHERE product_id = ?"
}

@DisplayName("bucket 모드 확정된 예약 취소 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8"])
class BucketConfirmedReservationCancelMySqlIntegrationTest : ConfirmedReservationCancelScenarios() {

    override fun seedStock(productId: Long, total: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = total))
        (0 until 8).forEach { bucketNo ->
            jdbcTemplate.update(
                "INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, ?, ?)",
                productId, bucketNo, total / 8
            )
        }
    }

    override fun committedStockSql(): String = "SELECT SUM(stock_quantity) FROM inventory_buckets WHERE product_id = ?"
}
