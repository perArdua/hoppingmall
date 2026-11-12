package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

abstract class ClaimedReservationScenarios : MySqlIntegrationTestSupport() {

    @Autowired
    protected lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    protected lateinit var inventoryReservationRepository: InventoryReservationRepository

    @Autowired
    protected lateinit var inventoryRepository: InventoryRepository

    protected abstract fun seedStock(productId: Long, total: Int)

    protected abstract fun stock(productId: Long): Int

    protected abstract fun runExpiry()

    @Test
    fun CLAIMED_예약을_확정하면_CONFIRMED가_되고_재고는_그대로다() {
        val reservationId = claimedReservation()

        val result = inventoryCommandService.confirmReservations(listOf(reservationId))

        assertThat(result).isTrue()
        assertThat(status(reservationId)).isEqualTo("CONFIRMED")
        assertThat(stock(PRODUCT_ID)).isEqualTo(77)
    }

    @Test
    fun CLAIMED_예약과_RESERVED_예약을_함께_확정하면_모두_CONFIRMED가_된다() {
        val claimed = claimedReservation()
        val reserved = inventoryCommandService.reserveStock(PRODUCT_ID, 2)

        val result = inventoryCommandService.confirmReservations(listOf(claimed, reserved))

        assertThat(result).isTrue()
        assertThat(status(claimed)).isEqualTo("CONFIRMED")
        assertThat(status(reserved)).isEqualTo("CONFIRMED")
        assertThat(stock(PRODUCT_ID)).isEqualTo(75)
    }

    @Test
    fun CLAIMED_예약을_단건_취소하면_CANCELLED가_되고_재고가_복원된다() {
        val reservationId = claimedReservation()

        inventoryCommandService.cancelReservation(reservationId)

        assertThat(status(reservationId)).isEqualTo("CANCELLED")
        assertThat(stock(PRODUCT_ID)).isEqualTo(80)
    }

    @Test
    fun CLAIMED_예약을_배치_취소하면_CANCELLED가_되고_재고가_한_번만_복원된다() {
        val claimed = claimedReservation()
        val reserved = inventoryCommandService.reserveStock(PRODUCT_ID, 2)

        inventoryCommandService.cancelReservations(listOf(claimed, reserved))
        inventoryCommandService.cancelReservations(listOf(claimed, reserved))

        assertThat(status(claimed)).isEqualTo("CANCELLED")
        assertThat(status(reserved)).isEqualTo("CANCELLED")
        assertThat(stock(PRODUCT_ID)).isEqualTo(80)
    }

    @Test
    fun 만료_시각이_지난_CLAIMED_예약은_만료_처리_대상이_아니다() {
        val claimed = claimedReservation()
        val reserved = inventoryCommandService.reserveStock(PRODUCT_ID, 2)
        expireNow(claimed)
        expireNow(reserved)

        runExpiry()

        assertThat(status(claimed)).isEqualTo("CLAIMED")
        assertThat(status(reserved)).isEqualTo("EXPIRED")
        assertThat(stock(PRODUCT_ID)).isEqualTo(77)
    }

    private fun claimedReservation(): String {
        seedStock(PRODUCT_ID, 80)
        val reservationId = inventoryCommandService.reserveStock(PRODUCT_ID, 3)
        assertThat(
            jdbcTemplate.update("UPDATE inventory_reservations SET status = 'CLAIMED' WHERE reservation_id = ?", reservationId)
        ).isEqualTo(1)
        assertThat(stock(PRODUCT_ID)).isEqualTo(77)
        return reservationId
    }

    private fun expireNow(reservationId: String) {
        jdbcTemplate.update(
            "UPDATE inventory_reservations SET expires_at = ? WHERE reservation_id = ?",
            LocalDateTime.now().minusMinutes(5), reservationId
        )
    }

    private fun status(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    companion object {
        const val PRODUCT_ID = 13_001L
    }
}

@DisplayName("single 모드 CLAIMED 예약 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SingleClaimedReservationMySqlIntegrationTest : ClaimedReservationScenarios() {

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    override fun seedStock(productId: Long, total: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = total))
    }

    override fun stock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!

    override fun runExpiry() {
        val scheduler = ReservationExpiryScheduler(inventoryReservationRepository, inventoryRepository)
        transactionTemplate.execute { scheduler.expireReservations() }
    }
}

@DisplayName("bucket 모드 CLAIMED 예약 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=64"])
class BucketClaimedReservationMySqlIntegrationTest : ClaimedReservationScenarios() {

    @Autowired
    private lateinit var bucketStockService: BucketStockService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    override fun seedStock(productId: Long, total: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = total))
        (0 until 8).forEach { bucketNo ->
            jdbcTemplate.update(
                "INSERT INTO inventory_buckets (product_id, bucket_no, stock_quantity) VALUES (?, ?, ?)",
                productId, bucketNo, total / 8
            )
        }
    }

    override fun stock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT SUM(stock_quantity) FROM inventory_buckets WHERE product_id = ?", Int::class.java, productId)!!

    override fun runExpiry() {
        BucketReservationExpiryJob(
            inventoryReservationRepository, bucketStockService, transactionManager, SimpleMeterRegistry(), true, 1000
        ).expireReservations()
    }
}
