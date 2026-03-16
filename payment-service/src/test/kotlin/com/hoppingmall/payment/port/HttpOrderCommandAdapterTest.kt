package com.hoppingmall.payment.port

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate

@DisplayName("HttpOrderCommandAdapter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class HttpOrderCommandAdapterTest {

    private val restTemplate = RestTemplate()
    private val server = MockRestServiceServer.createServer(restTemplate)
    private val adapter = HttpOrderCommandAdapter("http://localhost:8084", restTemplate)

    @Test
    fun 주문_취소_성공_시_true를_반환한다() {
        server.expect(requestTo("http://localhost:8084/internal/api/v1/orders/1/cancel"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON))

        val result = adapter.cancelOrder(1L)

        assertThat(result).isTrue()
        server.verify()
    }

    @Test
    fun 주문_취소_실패_시_예외를_던진다() {
        server.expect(requestTo("http://localhost:8084/internal/api/v1/orders/1/cancel"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThatThrownBy { adapter.cancelOrder(1L) }
            .isInstanceOf(HttpServerErrorException::class.java)

        server.verify()
    }
}
