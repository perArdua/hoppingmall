package com.hoppingmall.product.statistics.port

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
@Profile("!grpc")
class HttpCartItemQueryAdapter(
    @Value("\${services.order-service.url:http://localhost:8084}") private val orderServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : CartItemQueryPort {

    private val logger = LoggerFactory.getLogger(HttpCartItemQueryAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    @CircuitBreaker(name = "cart-query", fallbackMethod = "aggregateCartByProductFallback")
    @Retry(name = "cart-query")
    override fun aggregateCartByProduct(): List<CartAggregation> {
        val result = restTemplate.getForObject(
            "$orderServiceUrl/internal/api/v1/cart-items/aggregate",
            Array<CartAggregation>::class.java
        )
        return result?.toList() ?: emptyList()
    }

    private fun aggregateCartByProductFallback(e: Exception): List<CartAggregation> {
        logger.warn("CB fallback: 장바구니 집계 조회 실패", e)
        return emptyList()
    }
}
