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
class HttpProductQueryAdapter(
    @Value("\${services.product-service.url:http://localhost:8083}") private val productServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : ProductQueryPort {

    private val logger = LoggerFactory.getLogger(HttpProductQueryAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    @CircuitBreaker(name = "product-query", fallbackMethod = "findProductByIdFallback")
    @Retry(name = "product-query")
    override fun findProductById(productId: Long): ProductInfo? {
        return restTemplate.getForObject(
            "$productServiceUrl/internal/api/v1/products/$productId",
            ProductInfo::class.java
        )
    }

    @CircuitBreaker(name = "product-query", fallbackMethod = "findProductsByIdsFallback")
    @Retry(name = "product-query")
    override fun findProductsByIds(productIds: List<Long>): List<ProductInfo> {
        val ids = productIds.joinToString(",")
        val result = restTemplate.getForObject(
            "$productServiceUrl/internal/api/v1/products?ids=$ids",
            Array<ProductInfo>::class.java
        )
        return result?.toList() ?: emptyList()
    }

    private fun findProductByIdFallback(productId: Long, e: Exception): ProductInfo? {
        logger.warn("CB fallback: 상품 조회 실패 productId=$productId", e)
        return null
    }

    private fun findProductsByIdsFallback(productIds: List<Long>, e: Exception): List<ProductInfo> {
        logger.warn("CB fallback: 상품 목록 조회 실패 productIds=$productIds", e)
        return emptyList()
    }
}
