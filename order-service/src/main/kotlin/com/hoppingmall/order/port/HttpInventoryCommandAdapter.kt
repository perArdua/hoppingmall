package com.hoppingmall.order.port

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class HttpInventoryCommandAdapter(
    @Value("\${services.product-service.url:http://localhost:8083}") private val productServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : InventoryCommandPort {

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    override fun decreaseStock(productId: Long, quantity: Int) {
        try {
            restTemplate.postForEntity(
                "$productServiceUrl/internal/api/v1/inventory/$productId/decrease?quantity=$quantity",
                null,
                Void::class.java
            )
        } catch (e: Exception) {
            throw RuntimeException("재고 감소 실패: productId=$productId, quantity=$quantity", e)
        }
    }

    override fun increaseStock(productId: Long, quantity: Int) {
        try {
            restTemplate.postForEntity(
                "$productServiceUrl/internal/api/v1/inventory/$productId/increase?quantity=$quantity",
                null,
                Void::class.java
            )
        } catch (e: Exception) {
            throw RuntimeException("재고 증가 실패: productId=$productId, quantity=$quantity", e)
        }
    }
}
