package com.hoppingmall.payment.port

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.Duration

@Component
class HttpProductStatisticsAdapter(
    @Value("\${services.product-service.url:http://localhost:8083}") private val productServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : ProductStatisticsPort {

    private val logger = LoggerFactory.getLogger(HttpProductStatisticsAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    @CircuitBreaker(name = "inventory-command")
    @Retry(name = "default")
    override fun incrementRefundStats(productId: Long, quantity: Long, amount: BigDecimal) {
        restTemplate.postForEntity(
            "$productServiceUrl/internal/api/v1/product-statistics/$productId/refund?quantity=$quantity&amount=$amount",
            null,
            Void::class.java
        )
    }
}
