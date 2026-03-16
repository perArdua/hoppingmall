package com.hoppingmall.settlement.port

import com.hoppingmall.settlement.exception.ServiceCommunicationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class HttpSellerQueryAdapter(
    @Value("\${services.user-service.url}") private val userServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : SellerQueryPort {

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(10))
        .build()

    override fun findByUserId(userId: Long): SellerInfo? {
        try {
            val response = restTemplate.getForEntity(
                "$userServiceUrl/internal/api/v1/sellers/by-user/$userId",
                SellerResponse::class.java
            )
            val body = response.body ?: return null
            return SellerInfo(id = body.id, userId = body.userId)
        } catch (e: Exception) {
            throw ServiceCommunicationException("user-service 판매자 조회 실패: ${e.message}")
        }
    }

    data class SellerResponse(
        val id: Long = 0,
        val userId: Long = 0,
        val businessNumber: String = "",
        val approvalStatus: String = ""
    )
}
