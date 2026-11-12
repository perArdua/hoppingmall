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

@DisplayName("재고 예약 배치 취소 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class InventoryReservationBatchCancelMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

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

    private fun stock(): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, PRODUCT_ID)!!

    private fun reservationStatus(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    companion object {
        private const val PRODUCT_ID = 2001L
    }
}
