package com.hoppingmall.payment.port

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.Duration

@Component
@Profile("!grpc")
class HttpMembershipQueryAdapter(
    @Value("\${services.monolith.url:http://localhost:8080}") private val monolithUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : MembershipQueryPort {

    private val logger = LoggerFactory.getLogger(HttpMembershipQueryAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    @CircuitBreaker(name = "membership-query", fallbackMethod = "getPointEarningRateFallback")
    @Retry(name = "membership-query")
    override fun getPointEarningRate(userId: Long): BigDecimal {
        return restTemplate.getForObject(
            "$monolithUrl/internal/api/v1/memberships/by-user/$userId/earning-rate",
            BigDecimal::class.java
        ) ?: BigDecimal("0.01")
    }

    private fun getPointEarningRateFallback(userId: Long, e: Exception): BigDecimal {
        logger.warn("CB fallback: 멤버십 적립률 조회 실패 userId=$userId", e)
        return BigDecimal("0.01")
    }
}
