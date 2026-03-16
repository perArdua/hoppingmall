package com.hoppingmall.payment.port

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class HttpOrderCommandAdapter(
    private val orderServiceUrl: String,
    private val restTemplate: RestTemplate
) : OrderCommandPort {

    @Autowired
    constructor(
        @Value("\${services.order-service.url:http://localhost:8084}") orderServiceUrl: String,
        restTemplateBuilder: RestTemplateBuilder
    ) : this(
        orderServiceUrl,
        restTemplateBuilder.connectTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofSeconds(5)).build()
    )

    @CircuitBreaker(name = "order-command")
    override fun cancelOrder(orderId: Long): Boolean {
        restTemplate.postForEntity(
            "$orderServiceUrl/internal/api/v1/orders/$orderId/cancel",
            null,
            Void::class.java
        )
        return true
    }
}
