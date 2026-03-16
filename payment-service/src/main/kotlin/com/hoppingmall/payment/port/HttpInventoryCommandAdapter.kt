package com.hoppingmall.payment.port

import com.hoppingmall.payment.port.exception.InventoryRestoreFailedException
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class HttpInventoryCommandAdapter(
    private val productServiceUrl: String,
    private val restTemplate: RestTemplate
) : InventoryCommandPort {

    @Autowired
    constructor(
        @Value("\${services.product-service.url:http://localhost:8083}") productServiceUrl: String,
        restTemplateBuilder: RestTemplateBuilder
    ) : this(
        productServiceUrl,
        restTemplateBuilder.connectTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofSeconds(5)).build()
    )

    @CircuitBreaker(name = "inventory-command")
    override fun increaseStock(productId: Long, quantity: Int) {
        restTemplate.postForEntity(
            "$productServiceUrl/internal/api/v1/inventory/$productId/increase?quantity=$quantity",
            null,
            Void::class.java
        )
    }
}
