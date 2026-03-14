package com.hoppingmall.product.review.port

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class HttpOrderQueryAdapter(
    @Value("\${services.monolith.url:http://localhost:8080}") private val monolithUrl: String
) : OrderQueryPort {

    private val restTemplate = RestTemplate()

    override fun findOrderItemById(orderItemId: Long): OrderItemInfo? {
        return try {
            restTemplate.getForObject(
                "$monolithUrl/internal/api/v1/order-items/$orderItemId",
                OrderItemInfo::class.java
            )
        } catch (e: Exception) {
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
            emptyList()
        }
    }
}
