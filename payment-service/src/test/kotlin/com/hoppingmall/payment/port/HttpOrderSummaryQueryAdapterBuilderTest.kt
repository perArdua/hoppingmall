package com.hoppingmall.payment.port

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

@DisplayName("RestTemplateBuilder로 만든 HttpOrderSummaryQueryAdapter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class HttpOrderSummaryQueryAdapterBuilderTest {

    @Test
    fun 빌더로_만든_어댑터도_설정한_주문_서비스_주소의_내부_주문_조회_입구를_부른다() {
        val customizer = MockServerRestTemplateCustomizer()
        val adapter = HttpOrderSummaryQueryAdapter("http://order-service:8084", RestTemplateBuilder(customizer))
        customizer.server.expect(requestTo("http://order-service:8084/internal/api/v1/orders/9"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"code":"SUCCESS","message":"성공","data":{"orderId":9,"buyerId":3,"totalAmount":100,"status":"PAID"}}""",
                    MediaType.APPLICATION_JSON
                )
            )

        val result = adapter.findOrder(9L)

        assertThat(result?.buyerId).isEqualTo(3L)
        assertThat(result?.isAwaitingPayment()).isFalse()
        customizer.server.verify()
    }
}
