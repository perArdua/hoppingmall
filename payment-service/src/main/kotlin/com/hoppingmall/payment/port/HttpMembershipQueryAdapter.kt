package com.hoppingmall.payment.port

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.Duration

@Component
class HttpMembershipQueryAdapter(
    @Value("\${services.monolith.url:http://localhost:8080}") private val monolithUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : MembershipQueryPort {

    private val logger = LoggerFactory.getLogger(HttpMembershipQueryAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    override fun getPointEarningRate(userId: Long): BigDecimal {
        return try {
            val result = restTemplate.getForObject(
                "$monolithUrl/internal/api/v1/memberships/by-user/$userId/earning-rate",
                BigDecimal::class.java
            )
            result ?: BigDecimal("0.01")
        } catch (e: Exception) {
            logger.warn("멤버십 적립률 조회 실패: userId=$userId", e)
            BigDecimal("0.01")
        }
    }
}
