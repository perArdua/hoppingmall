package com.hoppingmall.payment.port

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
@Profile("!grpc")
class HttpOrderQueryAdapter(
    @Value("\${services.order-service.url:http://localhost:8084}") private val orderServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : OrderQueryPort {

    private val logger = LoggerFactory.getLogger(HttpOrderQueryAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    override fun findOrderItemsByOrderId(orderId: Long): List<OrderItemInfo> {
        return try {
            val result = restTemplate.getForObject(
                "$orderServiceUrl/internal/api/v1/orders/$orderId/items",
                Array<OrderItemInfo>::class.java
            )
            result?.toList() ?: emptyList()
        } catch (e: Exception) {
            logger.warn("주문 상품 조회 실패: orderId=$orderId", e)
            emptyList()
        }
    }
}
