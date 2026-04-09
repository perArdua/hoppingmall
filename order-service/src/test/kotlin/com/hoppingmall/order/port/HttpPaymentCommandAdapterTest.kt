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
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

@DisplayName("HttpPaymentCommandAdapter")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class HttpPaymentCommandAdapterTest {

    @Mock
    private lateinit var restTemplate: RestTemplate

    @Mock
    private lateinit var restTemplateBuilder: RestTemplateBuilder

    private lateinit var adapter: HttpPaymentCommandAdapter

    @BeforeEach
    fun setUp() {
        whenever(restTemplateBuilder.connectTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.readTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.build()).thenReturn(restTemplate)
        adapter = HttpPaymentCommandAdapter("http://localhost:8085", restTemplateBuilder)
    }

    @Test
    fun 결제_취소_성공_시_true를_반환한다() {
        whenever(restTemplate.postForEntity(
            any<String>(), eq(null), eq(Void::class.java)
        )).thenReturn(ResponseEntity.ok().build())

        val result = adapter.cancelPayment(1L)

        assertThat(result).isTrue()
    }

    @Test
    fun 결제_취소_API_호출에_성공하면_true를_반환한다() {
        whenever(restTemplate.postForEntity(
            any<String>(), eq(null), eq(Void::class.java)
        )).thenReturn(ResponseEntity.ok().build())

        val result = adapter.cancelPayment(100L)

        assertThat(result).isTrue()
    }
}
