package com.hoppingmall.settlement.port

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
import org.mockito.kotlin.whenever
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate

@ExtendWith(MockitoExtension::class)
@DisplayName("HttpSellerQueryAdapter 판매자 없음 응답")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class HttpSellerQueryAdapterNotFoundTest {

    @Mock
    private lateinit var restTemplateBuilder: RestTemplateBuilder

    private lateinit var server: MockRestServiceServer

    private lateinit var adapter: HttpSellerQueryAdapter

    @BeforeEach
    fun setUp() {
        val restTemplate = RestTemplate()
        server = MockRestServiceServer.bindTo(restTemplate).build()
        whenever(restTemplateBuilder.connectTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.readTimeout(any())).thenReturn(restTemplateBuilder)
        whenever(restTemplateBuilder.build()).thenReturn(restTemplate)
        adapter = HttpSellerQueryAdapter("http://localhost:8082", restTemplateBuilder)
    }

    @Test
    fun user_service가_404로_응답하면_판매자_없음으로_null을_돌려준다() {
        server.expect(requestTo("http://localhost:8082/internal/api/v1/sellers/by-user/10"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        val result = adapter.findByUserId(10L)

        assertThat(result).isNull()
        server.verify()
    }

    @Test
    fun user_service가_500으로_응답하면_예외를_그대로_던진다() {
        server.expect(requestTo("http://localhost:8082/internal/api/v1/sellers/by-user/10"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThatThrownBy { adapter.findByUserId(10L) }
            .isInstanceOf(HttpServerErrorException::class.java)
        server.verify()
    }
}
