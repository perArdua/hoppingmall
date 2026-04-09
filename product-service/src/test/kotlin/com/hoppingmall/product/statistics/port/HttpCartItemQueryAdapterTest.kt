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
import java.time.Duration

@DisplayName("HttpCartItemQueryAdapter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class HttpCartItemQueryAdapterTest {

    @Mock
    private lateinit var restTemplate: RestTemplate

    private lateinit var adapter: HttpCartItemQueryAdapter

    @BeforeEach
    fun setUp() {
        val restTemplateBuilder = object : RestTemplateBuilder() {
            override fun connectTimeout(connectTimeout: Duration): RestTemplateBuilder = this
            override fun readTimeout(readTimeout: Duration): RestTemplateBuilder = this
            override fun build(): RestTemplate = restTemplate
        }
        adapter = HttpCartItemQueryAdapter("http://localhost:8084", restTemplateBuilder)
    }

    @Test
    fun 장바구니_집계를_조회한다() {
        val aggregations = arrayOf(
            CartAggregation(productId = 1L, buyerCount = 10),
            CartAggregation(productId = 2L, buyerCount = 5)
        )

        whenever(
            restTemplate.getForObject(
                "http://localhost:8084/internal/api/v1/cart-items/aggregate",
                Array<CartAggregation>::class.java
            )
        ).thenReturn(aggregations)

        val result = adapter.aggregateCartByProduct()

        assertThat(result).hasSize(2)
        assertThat(result[0].productId).isEqualTo(1L)
    }

    @Test
    fun 장바구니_집계_null_응답_시_빈_리스트를_반환한다() {
        whenever(
            restTemplate.getForObject(
                "http://localhost:8084/internal/api/v1/cart-items/aggregate",
                Array<CartAggregation>::class.java
            )
        ).thenReturn(null)

        val result = adapter.aggregateCartByProduct()

        assertThat(result).isEmpty()
    }

    @Test
    fun CartAggregation을_생성한다() {
        val agg = CartAggregation(productId = 1L, buyerCount = 10)

        assertThat(agg.productId).isEqualTo(1L)
        assertThat(agg.buyerCount).isEqualTo(10)
    }
}
