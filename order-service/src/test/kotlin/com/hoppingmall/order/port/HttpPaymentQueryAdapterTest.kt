package com.hoppingmall.order.port

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal

@DisplayName("HttpPaymentQueryAdapter")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class HttpPaymentQueryAdapterTest {

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
        adapter = HttpPaymentQueryAdapter("http://localhost:8085", restTemplateBuilder)
    }

    @Test
    fun 주문ID로_결제정보를_조회한다() {
        val paymentInfo = PaymentInfo(
            id = 1L, orderId = 10L, amount = BigDecimal("50000"),
            pointAmount = BigDecimal("500"), couponId = null, status = "SUCCESS"
        )
        whenever(restTemplate.getForObject(
            any<String>(), eq(PaymentInfo::class.java)
        )).thenReturn(paymentInfo)

        val result = adapter.findByOrderId(10L)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(1L)
        assertThat(result.orderId).isEqualTo(10L)
    }

    @Test
    fun 주문ID로_결제정보가_없으면_null을_반환한다() {
        whenever(restTemplate.getForObject(
            any<String>(), eq(PaymentInfo::class.java)
        )).thenReturn(null)

        val result = adapter.findByOrderId(999L)

        assertThat(result).isNull()
    }

    @Test
    fun 결제ID로_결제정보를_조회한다() {
        val paymentInfo = PaymentInfo(
            id = 5L, orderId = 10L, amount = BigDecimal("50000"),
            pointAmount = BigDecimal("500"), couponId = 1L, status = "SUCCESS"
        )
        whenever(restTemplate.getForObject(
            any<String>(), eq(PaymentInfo::class.java)
        )).thenReturn(paymentInfo)

        val result = adapter.findById(5L)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(5L)
        assertThat(result.couponId).isEqualTo(1L)
    }

    @Test
    fun 결제ID로_결제정보가_없으면_null을_반환한다() {
        whenever(restTemplate.getForObject(
            any<String>(), eq(PaymentInfo::class.java)
        )).thenReturn(null)

        val result = adapter.findById(999L)

        assertThat(result).isNull()
    }

    @Test
    fun 주문별조회_CB_폴백_시_null을_반환한다() {
        val method = HttpPaymentQueryAdapter::class.java.getDeclaredMethod(
            "findByOrderIdFallback", Long::class.java, Exception::class.java
        )
        method.isAccessible = true

        val result = method.invoke(adapter, 1L, RuntimeException("fail"))

        assertThat(result).isNull()
    }

    @Test
    fun 결제조회_CB_폴백_시_null을_반환한다() {
        val method = HttpPaymentQueryAdapter::class.java.getDeclaredMethod(
            "findByIdFallback", Long::class.java, Exception::class.java
        )
        method.isAccessible = true

        val result = method.invoke(adapter, 1L, RuntimeException("fail"))

        assertThat(result).isNull()
    }
}
