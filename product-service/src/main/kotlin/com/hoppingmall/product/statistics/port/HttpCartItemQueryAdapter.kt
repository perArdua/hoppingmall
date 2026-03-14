package com.hoppingmall.product.statistics.port

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class HttpCartItemQueryAdapter(
    @Value("\${services.order-service.url:http://localhost:8084}") private val orderServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : CartItemQueryPort {

    private val logger = LoggerFactory.getLogger(HttpCartItemQueryAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    override fun aggregateCartByProduct(): List<CartAggregation> {
        return try {
            val result = restTemplate.getForObject(
                "$orderServiceUrl/internal/api/v1/cart-items/aggregate",
                Array<CartAggregation>::class.java
            )
            result?.toList() ?: emptyList()
        } catch (e: Exception) {
            logger.warn("장바구니 집계 조회 실패", e)
            emptyList()
        }
    }
}
