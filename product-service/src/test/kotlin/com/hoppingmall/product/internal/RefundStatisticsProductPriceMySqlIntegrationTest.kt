package com.hoppingmall.product.internal

import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.product.service.ProductStatisticsCommandService
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import java.math.BigDecimal

@AutoConfigureMockMvc
@DisplayName("환불 통계 정가 기준 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class RefundStatisticsProductPriceMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @Autowired
    private lateinit var productStatisticsCommandService: ProductStatisticsCommandService

    @Value("\${internal.service.token}")
    private lateinit var internalToken: String

    @BeforeEach
    fun cleanStatistics() {
        jdbcTemplate.update("DELETE FROM product_statistics WHERE product_id = ?", PRODUCT_ID)
        productStatisticsRepository.saveAndFlush(
            ProductStatistics.create(productId = PRODUCT_ID, productName = "정가 환불 상품", sellerId = 1L, categoryId = 1L)
        )
    }

    @Test
    fun 쿠폰을_쓴_주문을_전부_환불하면_정가로_빼서_순매출이_0이_된다() {
        productStatisticsCommandService.incrementSalesStats(PRODUCT_ID, 2L, BigDecimal("20000"))

        refundStats(quantity = 2, amount = "14000", body = """{"product_price":"10000"}""")
            .andExpect { status { isOk() } }

        assertThat(column("total_refund_amount")).isEqualByComparingTo("20000")
        assertThat(column("net_revenue")).isEqualByComparingTo("0")
    }

    @Test
    fun 일부_수량만_환불하면_정가_곱하기_환불_수량만큼_뺀다() {
        productStatisticsCommandService.incrementSalesStats(PRODUCT_ID, 3L, BigDecimal("30000"))

        refundStats(quantity = 1, amount = "7000", body = """{"product_price":10000}""")
            .andExpect { status { isOk() } }

        assertThat(column("total_refund_amount")).isEqualByComparingTo("10000")
        assertThat(column("net_revenue")).isEqualByComparingTo("20000")
    }

    @Test
    fun 정가가_null이면_환불_금액으로_뺀다() {
        productStatisticsCommandService.incrementSalesStats(PRODUCT_ID, 2L, BigDecimal("20000"))

        refundStats(quantity = 2, amount = "14000", body = """{"product_price":null}""")
            .andExpect { status { isOk() } }

        assertThat(column("total_refund_amount")).isEqualByComparingTo("14000")
    }

    @Test
    fun 본문이_없는_옛_요청은_지금처럼_환불_금액으로_뺀다() {
        productStatisticsCommandService.incrementSalesStats(PRODUCT_ID, 2L, BigDecimal("20000"))

        refundStats(quantity = 2, amount = "14000", body = null)
            .andExpect { status { isOk() } }

        assertThat(column("total_refund_amount")).isEqualByComparingTo("14000")
        assertThat(column("net_revenue")).isEqualByComparingTo("6000")
    }

    private fun refundStats(quantity: Long, amount: String, body: String?): ResultActionsDsl =
        mockMvc.post("/internal/api/v1/product-statistics/$PRODUCT_ID/refund") {
            param("quantity", quantity.toString())
            param("amount", amount)
            header(TOKEN_HEADER, internalToken)
            if (body != null) {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }
        }

    private fun column(name: String): BigDecimal =
        jdbcTemplate.queryForObject(
            "SELECT $name FROM product_statistics WHERE product_id = ?",
            BigDecimal::class.java,
            PRODUCT_ID
        )!!

    companion object {
        private const val PRODUCT_ID = 7381L
        private const val TOKEN_HEADER = "X-Internal-Token"
    }
}
