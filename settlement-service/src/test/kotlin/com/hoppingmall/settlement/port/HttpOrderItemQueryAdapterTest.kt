package com.hoppingmall.settlement.port

import com.hoppingmall.settlement.exception.ServiceCommunicationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@DisplayName("HttpOrderItemQueryAdapter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class HttpOrderItemQueryAdapterTest {

    @Mock
    private lateinit var restTemplate: RestTemplate

    @Mock
    private lateinit var restTemplateBuilder: RestTemplateBuilder

    private lateinit var adapter: HttpOrderItemQueryAdapter

    @BeforeEach
    fun setUp() {
        whenever(restTemplateBuilder.connectTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.readTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.build()).thenReturn(restTemplate)
        adapter = HttpOrderItemQueryAdapter("http://localhost:8084", restTemplateBuilder)
    }

    @Test
    fun 배송완료_주문상품_조회_성공() {
        val sellerId = 1L
        val startDate = LocalDateTime.of(2024, 1, 1, 0, 0)
        val endDate = LocalDateTime.of(2024, 1, 31, 23, 59)
        val items = listOf(
            OrderItemInfo(
                id = 1L,
                orderId = 10L,
                sellerId = sellerId,
                productId = 100L,
                productName = "상품A",
                productPrice = BigDecimal("10000"),
                quantity = 2,
                totalPrice = BigDecimal("20000")
            )
        )
        whenever(
            restTemplate.exchange(
                any<String>(),
                any<HttpMethod>(),
                anyOrNull(),
                any<ParameterizedTypeReference<List<OrderItemInfo>>>()
            )
        ).thenReturn(ResponseEntity.ok(items))

        val result = adapter.findDeliveredItemsBySellerAndPeriod(sellerId, startDate, endDate)

        assertThat(result).hasSize(1)
        assertThat(result[0].sellerId).isEqualTo(sellerId)
        assertThat(result[0].productName).isEqualTo("상품A")
    }

    @Test
    fun 배송완료_주문상품_조회_실패_시_예외를_발생시킨다() {
        val sellerId = 1L
        val startDate = LocalDateTime.of(2024, 1, 1, 0, 0)
        val endDate = LocalDateTime.of(2024, 1, 31, 23, 59)
        whenever(
            restTemplate.exchange(
                any<String>(),
                any<HttpMethod>(),
                anyOrNull(),
                any<ParameterizedTypeReference<List<OrderItemInfo>>>()
            )
        ).thenThrow(RuntimeException("connection refused"))

        assertThatThrownBy {
            adapter.findDeliveredItemsBySellerAndPeriod(sellerId, startDate, endDate)
        }.isInstanceOf(ServiceCommunicationException::class.java)
    }
}
