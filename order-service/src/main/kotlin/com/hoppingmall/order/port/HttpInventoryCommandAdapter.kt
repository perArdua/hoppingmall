package com.hoppingmall.order.port

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

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    override fun decreaseStock(productId: Long, quantity: Int) {
        try {
            restTemplate.postForEntity(
                "$productServiceUrl/internal/api/v1/inventory/$productId/decrease?quantity=$quantity",
                null,
                Void::class.java
            )
        } catch (e: Exception) {
            throw RuntimeException("재고 감소 실패: productId=$productId, quantity=$quantity", e)
        }
    }

    override fun increaseStock(productId: Long, quantity: Int) {
        try {
            restTemplate.postForEntity(
                "$productServiceUrl/internal/api/v1/inventory/$productId/increase?quantity=$quantity",
                null,
                Void::class.java
            )
        } catch (e: Exception) {
            throw RuntimeException("재고 증가 실패: productId=$productId, quantity=$quantity", e)
        }
    }

    override fun reserveStock(productId: Long, quantity: Int): String {
        try {
            val response = restTemplate.postForEntity(
                "$productServiceUrl/internal/api/v1/inventory/$productId/reserve?quantity=$quantity",
                null,
                ReservationResponse::class.java
            )
            return response.body?.reservationId
                ?: throw RuntimeException("재고 예약 응답 없음: productId=$productId")
        } catch (e: Exception) {
            if (e is RuntimeException && e.message?.startsWith("재고 예약 응답 없음") == true) throw e
            throw RuntimeException("재고 예약 실패: productId=$productId, quantity=$quantity", e)
        }
    }

    override fun confirmReservations(reservationIds: List<String>): Boolean {
        return try {
            val response = restTemplate.postForEntity(
                "$productServiceUrl/internal/api/v1/inventory/reservations/batch-confirm",
                reservationIds,
                ConfirmationResponse::class.java
            )
            response.body?.confirmed ?: false
        } catch (e: Exception) {
            false
        }
    }

    override fun cancelReservation(reservationId: String) {
        try {
            restTemplate.postForEntity(
                "$productServiceUrl/internal/api/v1/inventory/reservations/$reservationId/cancel",
                null,
                Void::class.java
            )
        } catch (e: Exception) {
            throw RuntimeException("예약 취소 실패: reservationId=$reservationId", e)
        }
    }

    override fun cancelReservations(reservationIds: List<String>) {
        try {
            restTemplate.postForEntity(
                "$productServiceUrl/internal/api/v1/inventory/reservations/batch-cancel",
                reservationIds,
                Void::class.java
            )
        } catch (e: Exception) {
            throw RuntimeException("예약 일괄 취소 실패: reservationIds=$reservationIds", e)
        }
    }

    data class ReservationResponse(val reservationId: String = "")
    data class ConfirmationResponse(val confirmed: Boolean = false)
}
