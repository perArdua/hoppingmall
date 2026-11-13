package com.hoppingmall.settlement.port

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

@ExtendWith(MockitoExtension::class)
@DisplayName("HttpSellerQueryAdapter 승인 상태")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class HttpSellerQueryAdapterApprovalTest {

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

    @ParameterizedTest
    @CsvSource("APPROVED, true", "PENDING, false", "REJECTED, false")
    fun 판매자의_승인_상태가_APPROVED일_때만_승인된_판매자로_돌려준다(approvalStatus: String, approved: Boolean) {
        whenever(
            restTemplate.getForEntity(
                eq("http://localhost:8082/internal/api/v1/sellers/by-user/10"),
                eq(HttpSellerQueryAdapter.SellerResponse::class.java)
            )
        ).thenReturn(
            ResponseEntity.ok(
                HttpSellerQueryAdapter.SellerResponse(
                    id = 5L,
                    userId = 10L,
                    businessNumber = "123-45-67890",
                    approvalStatus = approvalStatus
                )
            )
        )

        val result = adapter.findByUserId(10L)

        assertThat(result).isEqualTo(SellerInfo(id = 5L, userId = 10L, approved = approved))
    }
}
