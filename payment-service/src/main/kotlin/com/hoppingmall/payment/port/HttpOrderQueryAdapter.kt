package com.hoppingmall.payment.port

import com.hoppingmall.payment.port.exception.OrderItemQueryFailedException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
@Profile("!grpc")
class HttpOrderQueryAdapter(
    private val orderServiceUrl: String,
    private val restTemplate: RestTemplate
) : OrderQueryPort {

    @Autowired
    constructor(
        @Value("\${services.order-service.url:http://localhost:8084}") orderServiceUrl: String,
        restTemplateBuilder: RestTemplateBuilder
    ) : this(
        orderServiceUrl,
        restTemplateBuilder.connectTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofSeconds(5)).build()
    )

    private val logger = LoggerFactory.getLogger(HttpOrderQueryAdapter::class.java)

    override fun findOrderItemsByOrderId(orderId: Long): List<OrderItemInfo> {
        return try {
            val result = restTemplate.getForObject(
                "$orderServiceUrl/internal/api/v1/orders/$orderId/items",
                Array<OrderItemInfo>::class.java
            )
            result?.toList() ?: emptyList()
        } catch (e: Exception) {
            logger.error("주문 상품 조회 실패: orderId=$orderId", e)
            throw OrderItemQueryFailedException(orderId, e)
        }
    }
}
