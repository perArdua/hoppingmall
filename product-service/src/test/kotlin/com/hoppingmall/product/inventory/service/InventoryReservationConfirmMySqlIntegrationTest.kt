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
import org.springframework.test.context.TestPropertySource

abstract class InventoryReservationConfirmScenarios : MySqlIntegrationTestSupport() {

    @Autowired
    protected lateinit var inventoryCommandService: InventoryCommandService

    protected abstract fun seedStock(productId: Long, total: Int)

    protected abstract fun stock(productId: Long): Int

    @Test
    fun 전부_RESERVED면_확정에_성공하고_모두_CONFIRMED가_되며_재고는_그대로다() {
        seedStock(PRODUCT_ID, 80)
        seedStock(OTHER_PRODUCT_ID, 80)
        val first = inventoryCommandService.reserveStock(PRODUCT_ID, 3)
        val second = inventoryCommandService.reserveStock(OTHER_PRODUCT_ID, 5)

        val result = inventoryCommandService.confirmReservations(listOf(first, second))

        assertThat(result).isTrue()
        assertThat(status(first)).isEqualTo("CONFIRMED")
        assertThat(status(second)).isEqualTo("CONFIRMED")
        assertThat(stock(PRODUCT_ID)).isEqualTo(77)
        assertThat(stock(OTHER_PRODUCT_ID)).isEqualTo(75)
    }

    @Test
    fun 일부가_이미_EXPIRED면_확정은_예외로_끝나고_나머지_예약과_재고는_그대로다() {
        seedStock(PRODUCT_ID, 80)
        seedStock(OTHER_PRODUCT_ID, 80)
        val alive = inventoryCommandService.reserveStock(PRODUCT_ID, 3)
        val expired = inventoryCommandService.reserveStock(OTHER_PRODUCT_ID, 5)
        setStatus(expired, "EXPIRED")

        assertThatThrownBy { inventoryCommandService.confirmReservations(listOf(alive, expired)) }
            .isInstanceOf(ReservationConfirmFailedException::class.java)

        assertThat(status(alive)).isEqualTo("RESERVED")
        assertThat(status(expired)).isEqualTo("EXPIRED")
        assertThat(stock(PRODUCT_ID)).isEqualTo(77)
        assertThat(stock(OTHER_PRODUCT_ID)).isEqualTo(75)
    }

    @Test
    fun 일부가_이미_CANCELLED면_확정은_예외로_끝나고_나머지_예약과_재고는_그대로다() {
        seedStock(PRODUCT_ID, 80)
        seedStock(OTHER_PRODUCT_ID, 80)
        val alive = inventoryCommandService.reserveStock(PRODUCT_ID, 3)
        val cancelled = inventoryCommandService.reserveStock(OTHER_PRODUCT_ID, 5)
        inventoryCommandService.cancelReservation(cancelled)
        assertThat(stock(OTHER_PRODUCT_ID)).isEqualTo(80)

        assertThatThrownBy { inventoryCommandService.confirmReservations(listOf(alive, cancelled)) }
            .isInstanceOf(ReservationConfirmFailedException::class.java)

        assertThat(status(alive)).isEqualTo("RESERVED")
        assertThat(status(cancelled)).isEqualTo("CANCELLED")
        assertThat(stock(PRODUCT_ID)).isEqualTo(77)
        assertThat(stock(OTHER_PRODUCT_ID)).isEqualTo(80)
    }

    @Test
    fun 일부가_이미_CONFIRMED면_나머지를_확정하고_성공한다() {
        seedStock(PRODUCT_ID, 80)
        seedStock(OTHER_PRODUCT_ID, 80)
        val confirmed = inventoryCommandService.reserveStock(PRODUCT_ID, 3)
        val reserved = inventoryCommandService.reserveStock(OTHER_PRODUCT_ID, 5)
        assertThat(inventoryCommandService.confirmReservations(listOf(confirmed))).isTrue()

        val result = inventoryCommandService.confirmReservations(listOf(confirmed, reserved))

        assertThat(result).isTrue()
        assertThat(status(confirmed)).isEqualTo("CONFIRMED")
        assertThat(status(reserved)).isEqualTo("CONFIRMED")
        assertThat(stock(PRODUCT_ID)).isEqualTo(77)
        assertThat(stock(OTHER_PRODUCT_ID)).isEqualTo(75)
    }

    @Test
    fun 전부_이미_CONFIRMED인_확정을_다시_보내도_성공하고_아무것도_바뀌지_않는다() {
        seedStock(PRODUCT_ID, 80)
        val confirmed = inventoryCommandService.reserveStock(PRODUCT_ID, 3)
        assertThat(inventoryCommandService.confirmReservations(listOf(confirmed))).isTrue()

        val result = inventoryCommandService.confirmReservations(listOf(confirmed))

        assertThat(result).isTrue()
        assertThat(status(confirmed)).isEqualTo("CONFIRMED")
        assertThat(stock(PRODUCT_ID)).isEqualTo(77)
    }

    @Test
    fun 없는_예약_id가_섞이면_확정은_예외로_끝나고_있는_예약과_재고는_그대로다() {
        seedStock(PRODUCT_ID, 80)
        val alive = inventoryCommandService.reserveStock(PRODUCT_ID, 3)

        assertThatThrownBy { inventoryCommandService.confirmReservations(listOf(alive, "rsv-unknown")) }
            .isInstanceOf(ReservationConfirmFailedException::class.java)

        assertThat(status(alive)).isEqualTo("RESERVED")
        assertThat(stock(PRODUCT_ID)).isEqualTo(77)
        assertThat(reservationCount()).isEqualTo(1)
    }

    protected fun status(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    protected fun setStatus(reservationId: String, status: String) {
        assertThat(
            jdbcTemplate.update("UPDATE inventory_reservations SET status = ? WHERE reservation_id = ?", status, reservationId)
        ).isEqualTo(1)
    }

    protected fun reservationCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Int::class.java)!!

    companion object {
        const val PRODUCT_ID = 12_001L
        const val OTHER_PRODUCT_ID = 12_002L
    }
}

@DisplayName("single 모드 예약 확정 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SingleInventoryReservationConfirmMySqlIntegrationTest : InventoryReservationConfirmScenarios() {

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    override fun seedStock(productId: Long, total: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = total))
    }

    override fun stock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!
}

@DisplayName("bucket 모드 예약 확정 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestPropertySource(properties = ["inventory.mode=bucket", "inventory.bucket.count=8", "inventory.semaphore.permits=64"])
class BucketInventoryReservationConfirmMySqlIntegrationTest : InventoryReservationConfirmScenarios() {

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

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

    @Test
    fun 확정이_실패해도_재고_행의_수량은_바뀌지_않는다() {
        seedStock(PRODUCT_ID, 80)
        seedStock(OTHER_PRODUCT_ID, 80)
        val alive = inventoryCommandService.reserveStock(PRODUCT_ID, 3)
        val expired = inventoryCommandService.reserveStock(OTHER_PRODUCT_ID, 5)
        setStatus(expired, "EXPIRED")

        assertThatThrownBy { inventoryCommandService.confirmReservations(listOf(alive, expired)) }
            .isInstanceOf(ReservationConfirmFailedException::class.java)

        assertThat(inventoryRowStock(PRODUCT_ID)).isEqualTo(80)
        assertThat(inventoryRowStock(OTHER_PRODUCT_ID)).isEqualTo(80)
    }

    private fun inventoryRowStock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!
}
