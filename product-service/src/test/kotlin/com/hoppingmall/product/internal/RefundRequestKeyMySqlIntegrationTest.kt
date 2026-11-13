package com.hoppingmall.product.internal

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicLong

@AutoConfigureMockMvc
@DisplayName("환불 항목 요청 키 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class RefundRequestKeyMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var inventoryRepository: InventoryRepository

    @Autowired
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @Value("\${internal.service.token}")
    private lateinit var internalToken: String

    private val refundId = REFUND_SEQUENCE.incrementAndGet()

    @BeforeEach
    fun cleanStatistics() {
        jdbcTemplate.update("DELETE FROM product_statistics WHERE product_id IN (?, ?)", PRODUCT_A, PRODUCT_B)
    }

    @Test
    fun 같은_요청_키로_재고_증가를_두_번_보내면_재고는_한_번만_늘고_둘_다_성공한다() {
        seedInventory(PRODUCT_A, 10)

        increase(PRODUCT_A, 3, inventoryKey(1)).andExpect { status { isOk() } }
        increase(PRODUCT_A, 3, inventoryKey(1)).andExpect { status { isOk() } }

        assertThat(stock(PRODUCT_A)).isEqualTo(13)
    }

    @Test
    fun 요청_키가_없으면_지금처럼_보낼_때마다_재고가_는다() {
        seedInventory(PRODUCT_A, 10)

        increase(PRODUCT_A, 3, null).andExpect { status { isOk() } }
        increase(PRODUCT_A, 3, null).andExpect { status { isOk() } }

        assertThat(stock(PRODUCT_A)).isEqualTo(16)
    }

    @Test
    fun 같은_요청_키로_환불_통계를_두_번_보내면_한_번만_더해지고_둘_다_성공한다() {
        seedStatistics(PRODUCT_A)

        refundStats(PRODUCT_A, 2, "20000", statisticsKey(1)).andExpect { status { isOk() } }
        refundStats(PRODUCT_A, 2, "20000", statisticsKey(1)).andExpect { status { isOk() } }

        assertThat(refundQuantity(PRODUCT_A)).isEqualTo(2L)
        assertThat(refundAmount(PRODUCT_A)).isEqualByComparingTo("20000")
    }

    @Test
    fun 요청_키가_없으면_지금처럼_보낼_때마다_환불_통계가_더해진다() {
        seedStatistics(PRODUCT_A)

        refundStats(PRODUCT_A, 2, "20000", null).andExpect { status { isOk() } }
        refundStats(PRODUCT_A, 2, "20000", null).andExpect { status { isOk() } }

        assertThat(refundQuantity(PRODUCT_A)).isEqualTo(4L)
        assertThat(refundAmount(PRODUCT_A)).isEqualByComparingTo("40000")
    }

    @Test
    fun 같은_환불이라도_항목_키가_다르면_항목마다_재고와_통계를_반영한다() {
        seedInventory(PRODUCT_A, 10)
        seedInventory(PRODUCT_B, 10)
        seedStatistics(PRODUCT_A)
        seedStatistics(PRODUCT_B)

        increase(PRODUCT_A, 1, inventoryKey(1)).andExpect { status { isOk() } }
        increase(PRODUCT_B, 2, inventoryKey(2)).andExpect { status { isOk() } }
        refundStats(PRODUCT_A, 1, "10000", statisticsKey(1)).andExpect { status { isOk() } }
        refundStats(PRODUCT_B, 2, "30000", statisticsKey(2)).andExpect { status { isOk() } }

        assertThat(stock(PRODUCT_A)).isEqualTo(11)
        assertThat(stock(PRODUCT_B)).isEqualTo(12)
        assertThat(refundQuantity(PRODUCT_A)).isEqualTo(1L)
        assertThat(refundQuantity(PRODUCT_B)).isEqualTo(2L)
    }

    @Test
    fun 처리에_실패한_요청의_키는_남지_않아_같은_키로_다시_보내면_반영한다() {
        increase(PRODUCT_A, 3, inventoryKey(1)).andExpect { status { isNotFound() } }
        seedInventory(PRODUCT_A, 10)

        increase(PRODUCT_A, 3, inventoryKey(1)).andExpect { status { isOk() } }

        assertThat(stock(PRODUCT_A)).isEqualTo(13)
    }

    @Test
    fun 같은_요청_키를_다른_요청이_먼저_처리하고_있으면_그_요청이_끝난_뒤_재고를_바꾸지_않고_성공한다() {
        seedInventory(PRODUCT_A, 10)
        val key = inventoryKey(1)

        val result = runWhileHolding(
            holdingChanges = { connection ->
                connection.prepareStatement(
                    "INSERT INTO applied_request_keys (operation, request_key, created_at) VALUES ('INVENTORY_INCREASE', ?, NOW(6))"
                ).use {
                    it.setString(1, key)
                    it.executeUpdate()
                }
            },
            actionName = "같은 키의 재고 증가",
            waitTable = "applied_request_keys"
        ) {
            increase(PRODUCT_A, 3, key).andExpect { status { isOk() } }
        }

        assertThat(result.isSuccess).withFailMessage { result.exceptionOrNull().toString() }.isTrue()
        assertThat(stock(PRODUCT_A)).isEqualTo(10)
    }

    @Test
    fun 길이_한도를_넘는_요청_키는_400으로_거절하고_재고를_바꾸지_않는다() {
        seedInventory(PRODUCT_A, 10)

        increase(PRODUCT_A, 3, "k".repeat(65)).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("IRK001") }
        }

        assertThat(stock(PRODUCT_A)).isEqualTo(10)
    }

    private fun inventoryKey(orderItemId: Long) = "refund-$refundId-item-$orderItemId"

    private fun statisticsKey(orderItemId: Long) = "${inventoryKey(orderItemId)}-stat"

    private fun increase(productId: Long, quantity: Int, requestKey: String?): ResultActionsDsl =
        mockMvc.post("/internal/api/v1/inventory/$productId/increase") {
            param("quantity", quantity.toString())
            header(TOKEN_HEADER, internalToken)
            if (requestKey != null) header(REQUEST_KEY_HEADER, requestKey)
        }

    private fun refundStats(productId: Long, quantity: Long, amount: String, requestKey: String?): ResultActionsDsl =
        mockMvc.post("/internal/api/v1/product-statistics/$productId/refund") {
            param("quantity", quantity.toString())
            param("amount", amount)
            header(TOKEN_HEADER, internalToken)
            if (requestKey != null) header(REQUEST_KEY_HEADER, requestKey)
        }

    private fun seedInventory(productId: Long, stockQuantity: Int) {
        inventoryRepository.saveAndFlush(Inventory.create(productId = productId, stockQuantity = stockQuantity))
    }

    private fun seedStatistics(productId: Long) {
        productStatisticsRepository.saveAndFlush(
            ProductStatistics.create(productId = productId, productName = "환불 통계 상품", sellerId = 1L, categoryId = 1L)
        )
    }

    private fun stock(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Int::class.java, productId)!!

    private fun refundQuantity(productId: Long): Long =
        jdbcTemplate.queryForObject(
            "SELECT total_refund_quantity FROM product_statistics WHERE product_id = ?",
            Long::class.java,
            productId
        )!!

    private fun refundAmount(productId: Long): BigDecimal =
        jdbcTemplate.queryForObject(
            "SELECT total_refund_amount FROM product_statistics WHERE product_id = ?",
            BigDecimal::class.java,
            productId
        )!!

    companion object {
        private const val PRODUCT_A = 7101L
        private const val PRODUCT_B = 7102L
        private const val TOKEN_HEADER = "X-Internal-Token"
        private const val REQUEST_KEY_HEADER = "X-Request-Key"
        private val REFUND_SEQUENCE = AtomicLong(900_000L)
    }
}
