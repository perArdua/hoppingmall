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
@DisplayName("HttpRefundQueryAdapter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class HttpRefundQueryAdapterTest {

    @Mock
    private lateinit var restTemplate: RestTemplate

    @Mock
    private lateinit var restTemplateBuilder: RestTemplateBuilder

    private lateinit var adapter: HttpRefundQueryAdapter

    @BeforeEach
    fun setUp() {
        whenever(restTemplateBuilder.connectTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.readTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.build()).thenReturn(restTemplate)
        adapter = HttpRefundQueryAdapter("http://localhost:8084", restTemplateBuilder)
    }

    @Test
    fun 완료_환불_조회_성공() {
        val sellerId = 1L
        val startDate = LocalDateTime.of(2024, 1, 1, 0, 0)
        val endDate = LocalDateTime.of(2024, 1, 31, 23, 59)
        val refunds = listOf(
            RefundInfo(id = 1L, refundAmount = BigDecimal("5000"))
        )
        whenever(
            restTemplate.exchange(
                any<String>(),
                any<HttpMethod>(),
                anyOrNull(),
                any<ParameterizedTypeReference<List<RefundInfo>>>()
            )
        ).thenReturn(ResponseEntity.ok(refunds))

        val result = adapter.findCompletedBySellerAndPeriod(sellerId, startDate, endDate)

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(1L)
        assertThat(result[0].refundAmount).isEqualByComparingTo(BigDecimal("5000"))
    }

    @Test
    fun 완료_환불_조회_실패_시_예외를_발생시킨다() {
        val sellerId = 1L
        val startDate = LocalDateTime.of(2024, 1, 1, 0, 0)
        val endDate = LocalDateTime.of(2024, 1, 31, 23, 59)
        whenever(
            restTemplate.exchange(
                any<String>(),
                any<HttpMethod>(),
                anyOrNull(),
                any<ParameterizedTypeReference<List<RefundInfo>>>()
            )
        ).thenThrow(RuntimeException("connection refused"))

        assertThatThrownBy {
            adapter.findCompletedBySellerAndPeriod(sellerId, startDate, endDate)
        }.isInstanceOf(ServiceCommunicationException::class.java)
    }
}
