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

@DisplayName("내부 재고 증감 수량 검증 MySQL 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class StockChangeQuantityValidationMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var inventoryCommandService: InventoryCommandService

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Test
    fun 음수_수량으로_증가를_요청하면_재고가_줄지_않고_400_오류로_거절된다() {
        seed(10)

        val thrown = catchThrowable { inventoryCommandService.increaseStock(PRODUCT_ID, -3) }

        assertThat(stock()).isEqualTo(10)
        assertBadRequest(thrown)
    }

    @Test
    fun 음수_수량으로_감소를_요청하면_재고가_늘지_않고_400_오류로_거절된다() {
        seed(10)

        val thrown = catchThrowable { inventoryCommandService.decreaseStock(PRODUCT_ID, -3) }

        assertThat(stock()).isEqualTo(10)
        assertBadRequest(thrown)
    }

    @Test
    fun 수량_0으로_증가를_요청하면_400_오류로_거절된다() {
        seed(10)

        val thrown = catchThrowable { inventoryCommandService.increaseStock(PRODUCT_ID, 0) }

        assertThat(stock()).isEqualTo(10)
        assertBadRequest(thrown)
    }

    @Test
    fun 수량_0으로_감소를_요청하면_400_오류로_거절된다() {
        seed(10)

        val thrown = catchThrowable { inventoryCommandService.decreaseStock(PRODUCT_ID, 0) }

        assertThat(stock()).isEqualTo(10)
        assertBadRequest(thrown)
    }

    @Test
    fun 수량이_1_이상이면_지금처럼_증가하고_감소한다() {
        seed(10)

        inventoryCommandService.increaseStock(PRODUCT_ID, 4)
        inventoryCommandService.decreaseStock(PRODUCT_ID, 6)

        assertThat(stock()).isEqualTo(8)
    }

    private fun assertBadRequest(thrown: Throwable?) {
        assertThat(thrown).isInstanceOf(BusinessException::class.java)
        val errorCode = (thrown as BusinessException).errorCode
        assertThat(errorCode.status).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(errorCode.code).isEqualTo("INV008")
    }

    private fun seed(stockQuantity: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = PRODUCT_ID, stockQuantity = stockQuantity))
    }

    private fun stock(): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, PRODUCT_ID)!!

    companion object {
        private const val PRODUCT_ID = 8401L
    }
}
