package com.hoppingmall.order.port

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
@Profile("!grpc")
class HttpProductQueryAdapter(
    @Value("\${services.product-service.url:http://localhost:8083}") private val productServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : ProductQueryPort {

    private val logger = LoggerFactory.getLogger(HttpProductQueryAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    override fun findProductById(productId: Long): ProductInfo? {
        return try {
            restTemplate.getForObject(
                "$productServiceUrl/internal/api/v1/products/$productId",
                ProductInfo::class.java
            )
        } catch (e: Exception) {
            logger.warn("상품 조회 실패: productId=$productId", e)
            null
        }
    }

    override fun findProductsByIds(productIds: List<Long>): List<ProductInfo> {
        return try {
            val ids = productIds.joinToString(",")
            val result = restTemplate.getForObject(
                "$productServiceUrl/internal/api/v1/products?ids=$ids",
                Array<ProductInfo>::class.java
            )
            result?.toList() ?: emptyList()
        } catch (e: Exception) {
            logger.warn("상품 목록 조회 실패: productIds=$productIds", e)
            emptyList()
        }
    }

    override fun findProductImageUrl(productId: Long): String? {
        return try {
            restTemplate.getForObject(
                "$productServiceUrl/internal/api/v1/products/$productId/image-url",
                String::class.java
            )
        } catch (e: Exception) {
            logger.warn("상품 이미지 URL 조회 실패: productId=$productId", e)
            null
        }
    }
}
