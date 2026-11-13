package com.hoppingmall.payment.port

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal

@DisplayName("HttpOrderSummaryQueryAdapter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class HttpOrderSummaryQueryAdapterTest {

    private val restTemplate = RestTemplate(
        listOf(
            MappingJackson2HttpMessageConverter(
                jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            )
        )
    )
    private val server = MockRestServiceServer.createServer(restTemplate)
    private val adapter = HttpOrderSummaryQueryAdapter("http://localhost:8084", restTemplate)

    @Test
    fun 내부_주문_조회_입구를_불러_응답의_data를_주문_요약으로_읽는다() {
        val json = """{"code":"SUCCESS","message":"성공","data":{"order_id":7,"buyer_id":10,"total_amount":30000.00,"status":"PAYING"}}"""
        server.expect(requestTo("http://localhost:8084/internal/api/v1/orders/7"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON))

        val result = adapter.findOrder(7L)

        assertThat(result).isNotNull
        assertThat(result!!.orderId).isEqualTo(7L)
        assertThat(result.buyerId).isEqualTo(10L)
        assertThat(result.totalAmount).isEqualByComparingTo(BigDecimal("30000.00"))
        assertThat(result.status).isEqualTo("PAYING")
        server.verify()
    }

    @Test
    fun 응답_필드가_카멜_표기여도_같은_주문_요약으로_읽는다() {
        val json = """{"code":"SUCCESS","message":"성공","data":{"orderId":7,"buyerId":10,"totalAmount":30000.00,"status":"PAYING"}}"""
        server.expect(requestTo("http://localhost:8084/internal/api/v1/orders/7"))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON))

        val result = adapter.findOrder(7L)

        assertThat(result).isEqualTo(OrderSummary(7L, 10L, result!!.totalAmount, "PAYING"))
        assertThat(result.totalAmount).isEqualByComparingTo(BigDecimal("30000"))
        server.verify()
    }

    @Test
    fun 없는_주문이면_null을_돌려준다() {
        server.expect(requestTo("http://localhost:8084/internal/api/v1/orders/8"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertThat(adapter.findOrder(8L)).isNull()
        server.verify()
    }
}
