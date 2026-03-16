package com.hoppingmall.order.port

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class HttpInventoryCommandAdapter(
    @Value("\${services.product-service.url:http://localhost:8083}") private val productServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : InventoryCommandPort {

    private val logger = LoggerFactory.getLogger(HttpInventoryCommandAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    @CircuitBreaker(name = "inventory-command")
    override fun decreaseStock(productId: Long, quantity: Int) {
        restTemplate.postForEntity(
            "$productServiceUrl/internal/api/v1/inventory/$productId/decrease?quantity=$quantity",
            null,
            Void::class.java
        )
    }

    @CircuitBreaker(name = "inventory-command")
    override fun increaseStock(productId: Long, quantity: Int) {
        restTemplate.postForEntity(
            "$productServiceUrl/internal/api/v1/inventory/$productId/increase?quantity=$quantity",
            null,
            Void::class.java
        )
    }

    @CircuitBreaker(name = "inventory-command")
    override fun reserveStock(productId: Long, quantity: Int): String {
        val response = restTemplate.postForEntity(
            "$productServiceUrl/internal/api/v1/inventory/$productId/reserve?quantity=$quantity",
            null,
            ReservationResponse::class.java
        )
        return response.body?.reservationId
            ?: throw RuntimeException("재고 예약 응답 없음: productId=$productId")
    }

    @CircuitBreaker(name = "inventory-command", fallbackMethod = "confirmReservationsFallback")
    @Retry(name = "product-query")
    override fun confirmReservations(reservationIds: List<String>): Boolean {
        val response = restTemplate.postForEntity(
            "$productServiceUrl/internal/api/v1/inventory/reservations/batch-confirm",
            reservationIds,
            ConfirmationResponse::class.java
        )
        return response.body?.confirmed ?: false
    }

    @CircuitBreaker(name = "inventory-command")
    override fun cancelReservation(reservationId: String) {
        restTemplate.postForEntity(
            "$productServiceUrl/internal/api/v1/inventory/reservations/$reservationId/cancel",
            null,
            Void::class.java
        )
    }

    @CircuitBreaker(name = "inventory-command")
    override fun cancelReservations(reservationIds: List<String>) {
        restTemplate.postForEntity(
            "$productServiceUrl/internal/api/v1/inventory/reservations/batch-cancel",
            reservationIds,
            Void::class.java
        )
    }

    private fun confirmReservationsFallback(reservationIds: List<String>, e: Exception): Boolean {
        logger.warn("CB fallback: 예약 확정 실패 reservationIds=$reservationIds", e)
        return false
    }

    data class ReservationResponse(val reservationId: String = "")
    data class ConfirmationResponse(val confirmed: Boolean = false)
}
