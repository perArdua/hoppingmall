package com.hoppingmall.payment.port

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class HttpOrderCommandAdapter(
    @Value("\${services.order-service.url:http://localhost:8084}") private val orderServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : OrderCommandPort {

    private val logger = LoggerFactory.getLogger(HttpOrderCommandAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    override fun cancelOrder(orderId: Long): Boolean {
        return try {
            restTemplate.postForEntity(
                "$orderServiceUrl/internal/api/v1/orders/$orderId/cancel",
                null,
                Void::class.java
            )
            true
        } catch (e: Exception) {
            logger.warn("주문 취소 실패: orderId=$orderId", e)
            false
        }
    }
}
