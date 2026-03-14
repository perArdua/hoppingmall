package com.hoppingmall.order.port

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class HttpPaymentQueryAdapter(
    @Value("\${services.monolith.url:http://localhost:8080}") private val monolithUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : PaymentQueryPort {

    private val logger = LoggerFactory.getLogger(HttpPaymentQueryAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    override fun findByOrderId(orderId: Long): PaymentInfo? {
        return try {
            restTemplate.getForObject(
                "$monolithUrl/internal/api/v1/payments/by-order/$orderId",
                PaymentInfo::class.java
            )
        } catch (e: Exception) {
            logger.warn("주문별 결제 조회 실패: orderId=$orderId", e)
            null
        }
    }

    override fun findById(paymentId: Long): PaymentInfo? {
        return try {
            restTemplate.getForObject(
                "$monolithUrl/internal/api/v1/payments/$paymentId",
                PaymentInfo::class.java
            )
        } catch (e: Exception) {
            logger.warn("결제 조회 실패: paymentId=$paymentId", e)
            null
        }
    }
}
