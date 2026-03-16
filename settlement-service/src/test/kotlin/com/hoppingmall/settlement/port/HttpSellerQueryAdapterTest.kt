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
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

@ExtendWith(MockitoExtension::class)
@DisplayName("HttpSellerQueryAdapter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class HttpSellerQueryAdapterTest {

    @Mock
    private lateinit var restTemplate: RestTemplate

    @Mock
    private lateinit var restTemplateBuilder: RestTemplateBuilder

    private lateinit var adapter: HttpSellerQueryAdapter

    @BeforeEach
    fun setUp() {
        whenever(restTemplateBuilder.connectTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.readTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.build()).thenReturn(restTemplate)
        adapter = HttpSellerQueryAdapter("http://localhost:8082", restTemplateBuilder)
    }

    @Test
    fun 판매자_조회_성공() {
        val userId = 10L
        val sellerResponse = HttpSellerQueryAdapter.SellerResponse(
            id = 1L,
            userId = userId,
            businessNumber = "123-45-67890",
            approvalStatus = "APPROVED"
        )
        whenever(
            restTemplate.getForEntity(
                any<String>(),
                eq(HttpSellerQueryAdapter.SellerResponse::class.java)
            )
        ).thenReturn(ResponseEntity.ok(sellerResponse))

        val result = adapter.findByUserId(userId)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(1L)
        assertThat(result.userId).isEqualTo(userId)
    }

    @Test
    fun 판매자_조회_실패_시_예외를_발생시킨다() {
        val userId = 10L
        whenever(
            restTemplate.getForEntity(
                any<String>(),
                eq(HttpSellerQueryAdapter.SellerResponse::class.java)
            )
        ).thenThrow(RuntimeException("connection refused"))

        assertThatThrownBy {
            adapter.findByUserId(userId)
        }.isInstanceOf(ServiceCommunicationException::class.java)
    }
}
