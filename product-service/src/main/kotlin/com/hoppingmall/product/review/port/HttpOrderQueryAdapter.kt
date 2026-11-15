package com.hoppingmall.product.review.port

import com.hoppingmall.product.review.exception.OrderQueryUnavailableException
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
@Profile("!grpc")
class HttpOrderQueryAdapter(
    @Value("\${services.order-service.url:http://localhost:8084}") private val orderServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : OrderQueryPort {

    private val logger = LoggerFactory.getLogger(HttpOrderQueryAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    @CircuitBreaker(name = "order-query")
    @Retry(name = "order-query", fallbackMethod = "findOrderItemByIdFallback")
    override fun findOrderItemById(orderItemId: Long): OrderItemInfo? {
        return restTemplate.getForObject(
            "$orderServiceUrl/internal/api/v1/order-items/$orderItemId",
            OrderItemInfo::class.java
        )
    }

    @CircuitBreaker(name = "order-query")
    @Retry(name = "order-query", fallbackMethod = "isDeliveredFallback")
    override fun isDelivered(orderId: Long, buyerId: Long): Boolean {
        return restTemplate.getForObject(
            "$orderServiceUrl/internal/api/v1/orders/$orderId/delivered?buyerId=$buyerId",
            Boolean::class.java
        ) ?: false
    }

    @CircuitBreaker(name = "order-query")
    @Retry(name = "order-query", fallbackMethod = "findOrderItemsByOrderIdFallback")
    override fun findOrderItemsByOrderId(orderId: Long): List<OrderItemInfo> {
        val result = restTemplate.getForObject(
            "$orderServiceUrl/internal/api/v1/orders/$orderId/items",
            Array<OrderItemInfo>::class.java
        )
        return result?.toList() ?: emptyList()
    }

    private fun findOrderItemByIdFallback(orderItemId: Long, e: Exception): OrderItemInfo? {
        if (e is HttpClientErrorException.NotFound) return null
        logger.warn("주문 조회 실패(재시도 뒤): 주문 상품 orderItemId=$orderItemId", e)
        throw OrderQueryUnavailableException(e)
    }

    private fun isDeliveredFallback(orderId: Long, buyerId: Long, e: Exception): Boolean {
        if (e is HttpClientErrorException.NotFound) return false
        logger.warn("주문 조회 실패(재시도 뒤): 배송 완료 여부 orderId=$orderId, buyerId=$buyerId", e)
        throw OrderQueryUnavailableException(e)
    }

    private fun findOrderItemsByOrderIdFallback(orderId: Long, e: Exception): List<OrderItemInfo> {
        logger.warn("주문 조회 실패(재시도 뒤): 주문별 상품 목록 orderId=$orderId", e)
        throw OrderQueryUnavailableException(e)
    }
}
