package com.hoppingmall.order.port

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
class HttpPaymentQueryAdapter(
    @Value("\${services.monolith.url:http://localhost:8080}") private val monolithUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : PaymentQueryPort {

    private val logger = LoggerFactory.getLogger(HttpPaymentQueryAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    @CircuitBreaker(name = "payment-query", fallbackMethod = "findByOrderIdFallback")
    @Retry(name = "payment-query")
    override fun findByOrderId(orderId: Long): PaymentInfo? {
        return restTemplate.getForObject(
            "$monolithUrl/internal/api/v1/payments/by-order/$orderId",
            PaymentInfo::class.java
        )
    }

    @CircuitBreaker(name = "payment-query", fallbackMethod = "findByIdFallback")
    @Retry(name = "payment-query")
    override fun findById(paymentId: Long): PaymentInfo? {
        return restTemplate.getForObject(
            "$monolithUrl/internal/api/v1/payments/$paymentId",
            PaymentInfo::class.java
        )
    }

    private fun findByOrderIdFallback(orderId: Long, e: Exception): PaymentInfo? {
        logger.warn("CB fallback: 주문별 결제 조회 실패 orderId=$orderId", e)
        return null
    }

    private fun findByIdFallback(paymentId: Long, e: Exception): PaymentInfo? {
        logger.warn("CB fallback: 결제 조회 실패 paymentId=$paymentId", e)
        return null
    }
}
