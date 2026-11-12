package com.hoppingmall.product.inventory.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus

@DisplayName("재고 예약 수량 검증 MySQL 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ReserveQuantityValidationMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Test
    fun 음수_수량으로_단건_예약하면_재고가_늘지_않고_400_오류로_거절된다() {
        seed(PRODUCT_A, 10)

        val thrown = catchThrowable { inventoryCommandService.reserveStock(PRODUCT_A, -3) }

        assertThat(stock(PRODUCT_A)).isEqualTo(10)
        assertThat(reservationRowCount()).isZero()
        assertBadRequest(thrown)
    }

    @Test
    fun 수량_0으로_단건_예약하면_예약이_생기지_않고_400_오류로_거절된다() {
        seed(PRODUCT_A, 10)

        val thrown = catchThrowable { inventoryCommandService.reserveStock(PRODUCT_A, 0) }

        assertThat(reservationRowCount()).isZero()
        assertThat(stock(PRODUCT_A)).isEqualTo(10)
        assertBadRequest(thrown)
    }

    @Test
    fun 배치_예약에_음수_수량이_섞이면_어느_상품도_예약되지_않고_400_오류로_거절된다() {
        seed(PRODUCT_A, 10)
        seed(PRODUCT_B, 10)

        val thrown = catchThrowable {
            inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 2, PRODUCT_B to -3))
        }

        assertThat(listOf(stock(PRODUCT_A), stock(PRODUCT_B))).containsExactly(10, 10)
        assertThat(reservationRowCount()).isZero()
        assertBadRequest(thrown)
    }

    @Test
    fun 배치_예약에_수량_0이_섞이면_어느_상품도_예약되지_않고_400_오류로_거절된다() {
        seed(PRODUCT_A, 10)
        seed(PRODUCT_B, 10)

        val thrown = catchThrowable {
            inventoryCommandService.batchReserveStock(listOf(PRODUCT_A to 2, PRODUCT_B to 0))
        }

        assertThat(listOf(stock(PRODUCT_A), stock(PRODUCT_B))).containsExactly(10, 10)
        assertThat(reservationRowCount()).isZero()
        assertBadRequest(thrown)
    }

    @Test
    fun 수량이_1_이상이면_지금처럼_예약된다() {
        seed(PRODUCT_A, 10)

        inventoryCommandService.reserveStock(PRODUCT_A, 1)

        assertThat(stock(PRODUCT_A)).isEqualTo(9)
        assertThat(reservationRowCount()).isEqualTo(1)
    }

    private fun assertBadRequest(thrown: Throwable?) {
        assertThat(thrown).isInstanceOf(BusinessException::class.java)
        val errorCode = (thrown as BusinessException).errorCode
        assertThat(errorCode.status).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(errorCode.code).isEqualTo("INV007")
    }

    private fun seed(productId: Long, stockQuantity: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = stockQuantity))
    }

    private fun stock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!

    private fun reservationRowCount(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_reservations", Int::class.java)!!

    companion object {
        private const val PRODUCT_A = 7101L
        private const val PRODUCT_B = 7102L
    }
}
