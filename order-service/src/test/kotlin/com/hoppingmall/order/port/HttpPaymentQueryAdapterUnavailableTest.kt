package com.hoppingmall.order.port

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal

@DisplayName("주문별 결제 조회의 결제 없음과 조회 불가 구분")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class HttpPaymentQueryAdapterUnavailableTest {

    @Mock
    private lateinit var restTemplate: RestTemplate

    @Mock
    private lateinit var restTemplateBuilder: RestTemplateBuilder

    private lateinit var adapter: HttpPaymentQueryAdapter

    @BeforeEach
    fun setUp() {
        whenever(restTemplateBuilder.connectTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.readTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.build()).thenReturn(restTemplate)
        adapter = HttpPaymentQueryAdapter(BASE_URL, restTemplateBuilder)
    }

    @Test
    fun 결제가_있으면_결제_정보를_돌려준다() {
        val payment = PaymentInfo(
            id = 1L, orderId = 10L, amount = BigDecimal("50000"),
            pointAmount = BigDecimal.ZERO, couponId = null, status = "SUCCESS"
        )
        whenever(restTemplate.getForObject("$BASE_URL/internal/api/v1/payments/by-order/10", PaymentInfo::class.java))
            .thenReturn(payment)

        assertThat(adapter.findByOrderIdOrThrowIfUnavailable(10L)).isEqualTo(payment)
    }

    @Test
    fun 결제_서비스가_404면_결제_없음으로_null을_돌려준다() {
        whenever(restTemplate.getForObject("$BASE_URL/internal/api/v1/payments/by-order/10", PaymentInfo::class.java))
            .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, ByteArray(0), null))

        assertThat(adapter.findByOrderIdOrThrowIfUnavailable(10L)).isNull()
    }

    @Test
    fun 연결_실패로_fallback에_오면_조회_불가_예외를_던진다() {
        val fallback = HttpPaymentQueryAdapter::class.java.getDeclaredMethod(
            "findByOrderIdOrThrowIfUnavailableFallback", Long::class.java, Exception::class.java
        )
        fallback.isAccessible = true

        assertThatThrownBy { fallback.invoke(adapter, 10L, ResourceAccessException("connection refused")) }
            .hasCauseInstanceOf(PaymentQueryUnavailableException::class.java)
    }

    companion object {
        private const val BASE_URL = "http://localhost:8085"
    }
}
