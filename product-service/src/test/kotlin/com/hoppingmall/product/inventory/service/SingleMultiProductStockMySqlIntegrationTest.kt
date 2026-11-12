package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.domain.repository.InventoryReservationRepository
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

@DisplayName("single 모드 여러 상품 재고 반영 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SingleMultiProductStockMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var inventoryReservationRepository: InventoryReservationRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Test
    fun 만료_스케줄러가_여러_상품의_예약을_한_번에_만료하면_상품마다_재고가_돌아온다() {
        seedStocks()
        val reservations = inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 3, PRODUCT_B to 5, PRODUCT_C to 7))
        assertThat(stocks()).containsExactly(77, 35, 53)
        makeExpired(reservations.values)

        transactionTemplate.execute { ReservationExpiryScheduler(inventoryReservationRepository, inventoryRepository).expireReservations() }

        assertThat(reservations.values.map { status(it) }).containsOnly("EXPIRED")
        assertThat(stocks()).containsExactly(80, 40, 60)
    }

    @Test
    fun 여러_상품_배치_예약은_상품마다_줄어든_재고를_커밋한다() {
        seedStocks()

        val reservations = inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 3, PRODUCT_B to 5, PRODUCT_C to 7))

        assertThat(reservations.values.map { status(it) }).containsOnly("RESERVED")
        assertThat(stocks()).containsExactly(77, 35, 53)
    }

    @Test
    fun 여러_상품_배치_취소는_상품마다_재고를_되돌린다() {
        seedStocks()
        val reservations = inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 3, PRODUCT_B to 5, PRODUCT_C to 7))
        assertThat(stocks()).containsExactly(77, 35, 53)

        inventoryCommandService.cancelReservations(reservations.values.toList())

        assertThat(reservations.values.map { status(it) }).containsOnly("CANCELLED")
        assertThat(stocks()).containsExactly(80, 40, 60)
    }

    private fun seedStocks() {
        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_A, stockQuantity = 80))
        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_B, stockQuantity = 40))
        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_C, stockQuantity = 60))
    }

    private fun makeExpired(reservationIds: Collection<String>) {
        reservationIds.forEach {
            jdbcTemplate.update(
                "UPDATE inventory_reservations SET expires_at = ? WHERE reservation_id = ?",
                LocalDateTime.now().minusMinutes(1), it
            )
        }
    }

    private fun stocks(): List<Int> =
        listOf(PRODUCT_A, PRODUCT_B, PRODUCT_C).map {
            jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, it)!!
        }

    private fun status(reservationId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM inventory_reservations WHERE reservation_id = ?", String::class.java, reservationId)!!

    companion object {
        private const val PRODUCT_A = 21_001L
        private const val PRODUCT_B = 21_002L
        private const val PRODUCT_C = 21_003L
    }
}
