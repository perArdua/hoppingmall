package com.hoppingmall.payment.port

import com.hoppingmall.payment.port.exception.OrderCancellationFailedException
import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger(HttpOrderCommandAdapter::class.java)

    override fun cancelOrder(orderId: Long): Boolean {
        return try {
            restTemplate.postForEntity(
                "$orderServiceUrl/internal/api/v1/orders/$orderId/cancel",
                null,
                Void::class.java
            )
            true
        } catch (e: Exception) {
            logger.error("주문 취소 실패: orderId=$orderId", e)
            throw OrderCancellationFailedException(orderId, e)
        }
    }
}
