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
class HttpOrderItemQueryAdapter(
    @Value("\${services.order-service.url}") private val orderServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : OrderItemQueryPort {

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(10))
        .build()

    override fun findDeliveredItemsBySellerAndPeriod(
        sellerId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<OrderItemInfo> {
        try {
            val url = "$orderServiceUrl/internal/api/v1/order-items/delivered?sellerId=$sellerId&startDate=${startDate.format(DateTimeFormatter.ISO_DATE_TIME)}&endDate=${endDate.format(DateTimeFormatter.ISO_DATE_TIME)}"
            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                object : ParameterizedTypeReference<List<OrderItemInfo>>() {}
            )
            return response.body ?: emptyList()
        } catch (e: Exception) {
            throw ServiceCommunicationException("order-service 배송완료 주문상품 조회 실패: ${e.message}")
        }
    }
}
