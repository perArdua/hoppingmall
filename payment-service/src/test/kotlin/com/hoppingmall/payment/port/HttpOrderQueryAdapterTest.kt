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

@DisplayName("HttpOrderQueryAdapter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class HttpOrderQueryAdapterTest {

    private val restTemplate = RestTemplate()
    private val server = MockRestServiceServer.createServer(restTemplate)
    private val adapter = HttpOrderQueryAdapter("http://localhost:8084", restTemplate)

    @Test
    fun 주문_상품_조회_성공_시_리스트를_반환한다() {
        val json = """[{"id":1,"productId":10,"quantity":2,"totalPrice":"20000"}]"""
        server.expect(requestTo("http://localhost:8084/internal/api/v1/orders/1/items"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON))

        val result = adapter.findOrderItemsByOrderId(1L)

        assertThat(result).hasSize(1)
        assertThat(result[0].productId).isEqualTo(10L)
        assertThat(result[0].quantity).isEqualTo(2)
        server.verify()
    }

    @Test
    fun 주문_상품_조회_실패_시_예외를_던진다() {
        server.expect(requestTo("http://localhost:8084/internal/api/v1/orders/1/items"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThatThrownBy { adapter.findOrderItemsByOrderId(1L) }
            .isInstanceOf(HttpServerErrorException::class.java)

        server.verify()
    }
}
