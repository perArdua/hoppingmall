package com.hoppingmall.product.review.port

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class HttpOrderQueryAdapter(
    @Value("\${services.monolith.url:http://localhost:8080}") private val monolithUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : OrderQueryPort {

    private val logger = LoggerFactory.getLogger(HttpOrderQueryAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    override fun findOrderItemById(orderItemId: Long): OrderItemInfo? {
        return try {
            restTemplate.getForObject(
                "$monolithUrl/internal/api/v1/order-items/$orderItemId",
                OrderItemInfo::class.java
            )
        } catch (e: Exception) {
            logger.warn("주문 상품 조회 실패: orderItemId=$orderItemId", e)
            null
        }
    }

    override fun isDelivered(orderId: Long, buyerId: Long): Boolean {
        return try {
            restTemplate.getForObject(
                "$monolithUrl/internal/api/v1/orders/$orderId/delivered?buyerId=$buyerId",
                Boolean::class.java
            ) ?: false
        } catch (e: Exception) {
            logger.warn("배송 완료 여부 조회 실패: orderId=$orderId, buyerId=$buyerId", e)
            false
        }
    }

    override fun findOrderItemsByOrderId(orderId: Long): List<OrderItemInfo> {
        return try {
            val result = restTemplate.getForObject(
                "$monolithUrl/internal/api/v1/orders/$orderId/items",
                Array<OrderItemInfo>::class.java
            )
            result?.toList() ?: emptyList()
        } catch (e: Exception) {
            logger.warn("주문별 상품 목록 조회 실패: orderId=$orderId", e)
            emptyList()
        }
    }
}
