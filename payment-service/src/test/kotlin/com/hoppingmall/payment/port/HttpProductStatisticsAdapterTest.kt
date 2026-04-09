package com.hoppingmall.payment.port

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal

@DisplayName("HttpProductStatisticsAdapter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class HttpProductStatisticsAdapterTest {

    private val restTemplate = RestTemplate()
    private val server = MockRestServiceServer.createServer(restTemplate)
    private lateinit var adapter: HttpProductStatisticsAdapter

    @BeforeEach
    fun setUp() {
        adapter = HttpProductStatisticsAdapter("http://localhost:8083", RestTemplateBuilder())
        ReflectionTestUtils.setField(adapter, "restTemplate", restTemplate)
    }

    @Test
    fun 환불_통계_업데이트_성공_시_정상_완료된다() {
        server.expect(requestTo("http://localhost:8083/internal/api/v1/product-statistics/10/refund?quantity=2&amount=5000"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON))

        assertDoesNotThrow { adapter.incrementRefundStats(10L, 2L, BigDecimal("5000")) }
        server.verify()
    }

    @Test
    fun 환불_통계_업데이트_실패_시_예외를_던진다() {
        server.expect(requestTo("http://localhost:8083/internal/api/v1/product-statistics/10/refund?quantity=2&amount=5000"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThatThrownBy { adapter.incrementRefundStats(10L, 2L, BigDecimal("5000")) }
            .isInstanceOf(HttpServerErrorException::class.java)
        server.verify()
    }
}
