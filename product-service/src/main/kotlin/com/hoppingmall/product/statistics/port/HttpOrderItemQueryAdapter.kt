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
class HttpOrderItemQueryAdapter(
    @Value("\${services.order-service.url:http://localhost:8084}") private val orderServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : OrderItemQueryPort {

    private val logger = LoggerFactory.getLogger(HttpOrderItemQueryAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    @CircuitBreaker(name = "order-item-query", fallbackMethod = "findByOrderIdFallback")
    @Retry(name = "order-item-query")
    override fun findByOrderId(orderId: Long): List<OrderItemInfo> {
        val result = restTemplate.getForObject(
            "$orderServiceUrl/internal/api/v1/orders/$orderId/items",
            Array<OrderItemInfo>::class.java
        )
        return result?.toList() ?: emptyList()
    }

    private fun findByOrderIdFallback(orderId: Long, e: Exception): List<OrderItemInfo> {
        logger.warn("CB fallback: 주문 상품 조회 실패 orderId=$orderId", e)
        return emptyList()
    }
}
