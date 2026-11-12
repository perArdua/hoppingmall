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
import java.sql.Connection
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DisplayName("재고 예약 취소 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class InventoryReservationCancelMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Test
    fun 같은_예약을_동시에_여러_번_취소해도_재고는_한_번만_복원된다() {
        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_ID, stockQuantity = 10))
        val reservationId = inventoryCommandService.reserveStock(PRODUCT_ID, 3)
        assertThat(stock()).isEqualTo(7)

        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        repeat(threads) {
            pool.submit {
                start.await()
                runCatching { inventoryCommandService.cancelReservation(reservationId) }.onFailure { errors.add(it) }
            }
        }
        start.countDown()
        pool.shutdown()
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue()

        assertThat(errors).isEmpty()
        assertThat(stock()).isEqualTo(10)
        assertThat(reservationStatus(reservationId)).isEqualTo("CANCELLED")
    }

    @Test
    fun 단건_취소가_만료_처리와_겹치면_만료가_복원한_수량을_다시_복원하지_않는다() {
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
    fun 단건_취소가_예약_확정과_겹치면_확정된_예약의_수량을_복원하지_않는다() {
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

    @Test
    fun 없는_예약을_단건_취소하면_예외_없이_끝나고_아무것도_바뀌지_않는다() {
        val reservationId = reserveThree()

        inventoryCommandService.cancelReservation("rsv-unknown")

        assertThat(stock()).isEqualTo(7)
        assertThat(reservationStatus(reservationId)).isEqualTo("RESERVED")
        assertThat(reservationCount()).isEqualTo(1)
    }

    @Test
    fun 이미_취소된_예약을_다시_단건_취소해도_재고는_한_번만_복원된다() {
        val reservationId = reserveThree()

        inventoryCommandService.cancelReservation(reservationId)
        inventoryCommandService.cancelReservation(reservationId)

        assertThat(stock()).isEqualTo(10)
        assertThat(reservationStatus(reservationId)).isEqualTo("CANCELLED")
    }

    @Test
    fun 이미_만료된_예약을_단건_취소해도_재고가_그대로다() {
        val reservationId = reserveThree()
        jdbcTemplate.update("UPDATE inventory_reservations SET status = 'EXPIRED' WHERE reservation_id = ?", reservationId)

        inventoryCommandService.cancelReservation(reservationId)

        assertThat(stock()).isEqualTo(7)
        assertThat(reservationStatus(reservationId)).isEqualTo("EXPIRED")
    }

    private fun reserveThree(): String {
        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_ID, stockQuantity = 10))
        val reservationId = inventoryCommandService.reserveStock(PRODUCT_ID, 3)
        assertThat(stock()).isEqualTo(7)
        return reservationId
    }

    private fun cancelWhileHolding(reservationId: String, holdingChanges: (Connection) -> Unit): Result<Unit> =
        runWhileHolding(holdingChanges, "단건 취소", "inventory_reservations") {
            inventoryCommandService.cancelReservation(reservationId)
        }

    private fun stock(): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, PRODUCT_ID)!!

    private fun reservationStatus(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    private fun reservationCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Int::class.java)!!

    companion object {
        private const val PRODUCT_ID = 1001L
    }
}
