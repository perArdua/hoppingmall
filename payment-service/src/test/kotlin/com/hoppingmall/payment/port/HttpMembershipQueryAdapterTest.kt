package com.hoppingmall.payment.port

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.MediaType
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

@DisplayName("HttpMembershipQueryAdapter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class HttpMembershipQueryAdapterTest {

    private val restTemplate = RestTemplate()
    private val server = MockRestServiceServer.createServer(restTemplate)
    private lateinit var adapter: HttpMembershipQueryAdapter

    @BeforeEach
    fun setUp() {
        adapter = HttpMembershipQueryAdapter("http://localhost:8082", RestTemplateBuilder())
        ReflectionTestUtils.setField(adapter, "restTemplate", restTemplate)
    }

    @Test
    fun 적립률_조회_성공_시_BigDecimal을_반환한다() {
        server.expect(requestTo("http://localhost:8082/internal/api/v1/memberships/by-user/1/earning-rate"))
            .andRespond(withSuccess("0.05", MediaType.APPLICATION_JSON))

        val result = adapter.getPointEarningRate(1L)

        assertThat(result).isEqualByComparingTo("0.05")
        server.verify()
    }

    @Test
    fun 응답이_null이면_기본값_0_01을_반환한다() {
        server.expect(requestTo("http://localhost:8082/internal/api/v1/memberships/by-user/2/earning-rate"))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON))

        val result = adapter.getPointEarningRate(2L)

        assertThat(result).isEqualByComparingTo("0.01")
        server.verify()
    }
}
