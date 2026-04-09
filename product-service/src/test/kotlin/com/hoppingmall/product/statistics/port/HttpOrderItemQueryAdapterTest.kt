package com.hoppingmall.product.statistics.port

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.Duration

@DisplayName("HttpOrderItemQueryAdapter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class HttpOrderItemQueryAdapterTest {

    @Mock
    private lateinit var restTemplate: RestTemplate

    private lateinit var adapter: HttpOrderItemQueryAdapter

    @BeforeEach
    fun setUp() {
        val restTemplateBuilder = object : RestTemplateBuilder() {
            override fun connectTimeout(connectTimeout: Duration): RestTemplateBuilder = this
            override fun readTimeout(readTimeout: Duration): RestTemplateBuilder = this
            override fun build(): RestTemplate = restTemplate
        }
        adapter = HttpOrderItemQueryAdapter("http://localhost:8084", restTemplateBuilder)
    }

    @Test
    fun 주문별_상품_목록을_조회한다() {
        val items = arrayOf(
            OrderItemInfo(id = 1L, orderId = 10L, productId = 1L, quantity = 2, totalPrice = BigDecimal("20000")),
            OrderItemInfo(id = 2L, orderId = 10L, productId = 2L, quantity = 1, totalPrice = BigDecimal("15000"))
        )

        whenever(
            restTemplate.getForObject(
                "http://localhost:8084/internal/api/v1/orders/10/items",
                Array<OrderItemInfo>::class.java
            )
        ).thenReturn(items)

        val result = adapter.findByOrderId(10L)

        assertThat(result).hasSize(2)
        assertThat(result[0].productId).isEqualTo(1L)
    }

    @Test
    fun 주문별_상품_목록_null_응답_시_빈_리스트를_반환한다() {
        whenever(
            restTemplate.getForObject(
                "http://localhost:8084/internal/api/v1/orders/10/items",
                Array<OrderItemInfo>::class.java
            )
        ).thenReturn(null)

        val result = adapter.findByOrderId(10L)

        assertThat(result).isEmpty()
    }

    @Test
    fun OrderItemInfo를_생성한다() {
        val info = OrderItemInfo(
            id = 1L, orderId = 10L, productId = 1L, quantity = 2, totalPrice = BigDecimal("20000")
        )

        assertThat(info.id).isEqualTo(1L)
        assertThat(info.orderId).isEqualTo(10L)
        assertThat(info.productId).isEqualTo(1L)
        assertThat(info.quantity).isEqualTo(2)
        assertThat(info.totalPrice).isEqualByComparingTo(BigDecimal("20000"))
    }
}
