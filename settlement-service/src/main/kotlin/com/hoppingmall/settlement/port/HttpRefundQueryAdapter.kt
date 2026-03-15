package com.hoppingmall.settlement.port

import com.hoppingmall.settlement.exception.ServiceCommunicationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class HttpRefundQueryAdapter(
    @Value("\${services.order-service.url}") private val orderServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : RefundQueryPort {

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(10))
        .build()

    override fun findCompletedBySellerAndPeriod(
        sellerId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<RefundInfo> {
        try {
            val url = "$orderServiceUrl/internal/api/v1/refunds/completed?sellerId=$sellerId&startDate=${startDate.format(DateTimeFormatter.ISO_DATE_TIME)}&endDate=${endDate.format(DateTimeFormatter.ISO_DATE_TIME)}"
            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                object : ParameterizedTypeReference<List<RefundInfo>>() {}
            )
            return response.body ?: emptyList()
        } catch (e: Exception) {
            throw ServiceCommunicationException("order-service 완료 환불 조회 실패: ${e.message}")
        }
    }
}
