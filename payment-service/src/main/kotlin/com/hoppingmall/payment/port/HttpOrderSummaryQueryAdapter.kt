package com.hoppingmall.payment.port

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.payment.port.exception.OrderSummaryQueryFailedException
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class HttpOrderSummaryQueryAdapter(
    private val orderServiceUrl: String,
    private val restTemplate: RestTemplate
) : OrderSummaryQueryPort {

    @Autowired
    constructor(
        @Value("\${services.order-service.url:http://localhost:8084}") orderServiceUrl: String,
        restTemplateBuilder: RestTemplateBuilder
    ) : this(
        orderServiceUrl,
        restTemplateBuilder.connectTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofSeconds(5)).build()
    )

    private val logger = LoggerFactory.getLogger(HttpOrderSummaryQueryAdapter::class.java)

    @CircuitBreaker(name = "order-query", fallbackMethod = "findOrderFallback")
    @Retry(name = "order-query")
    override fun findOrder(orderId: Long): OrderSummary? =
        try {
            restTemplate.exchange(
                "$orderServiceUrl/internal/api/v1/orders/$orderId",
                HttpMethod.GET,
                null,
                object : ParameterizedTypeReference<ApiResponse<OrderSummary>>() {}
            ).body?.data
        } catch (e: HttpClientErrorException.NotFound) {
            null
        }

    private fun findOrderFallback(orderId: Long, e: Exception): OrderSummary? {
        logger.error("CB fallback: 주문 조회 실패 orderId=$orderId", e)
        throw OrderSummaryQueryFailedException(orderId, e)
    }
}
