package com.hoppingmall.order.port

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class HttpPaymentCommandAdapter(
    @Value("\${services.payment-service.url:http://localhost:8085}") private val paymentServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : PaymentCommandPort {

    private val logger = LoggerFactory.getLogger(HttpPaymentCommandAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    @CircuitBreaker(name = "payment-command", fallbackMethod = "cancelPaymentFallback")
    @Retry(name = "payment-command")
    override fun cancelPayment(orderId: Long): PaymentCancellationResult {
        try {
            restTemplate.postForEntity(
                "$paymentServiceUrl/internal/api/v1/payments/by-order/$orderId/cancel",
                null,
                Void::class.java
            )
        } catch (e: HttpClientErrorException.NotFound) {
            return PaymentCancellationResult.NOT_FOUND
        }
        return PaymentCancellationResult.CANCELLED
    }

    private fun cancelPaymentFallback(orderId: Long, e: Exception): PaymentCancellationResult {
        logger.warn("CB fallback: 결제 취소 실패 orderId=$orderId", e)
        return PaymentCancellationResult.FAILED
    }
}
