package com.hoppingmall.order.port

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.util.UUID

@Component
class HttpInventoryCommandAdapter(
    @Value("\${services.product-service.url:http://localhost:8083}") private val productServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : InventoryCommandPort {

    private val logger = LoggerFactory.getLogger(HttpInventoryCommandAdapter::class.java)

    @Value("\${order.reservation.fence.enabled:false}")
    private var budgetHeaderEnabled: Boolean = false

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(READ_TIMEOUT)
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

    @CircuitBreaker(name = "inventory-command")
    @Retry(name = "inventory-confirm")
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

    @CircuitBreaker(name = "inventory-command")
    override fun cancelConfirmedReservations(reservationIds: List<String>) {
        restTemplate.postForEntity(
            "$productServiceUrl/internal/api/v1/inventory/reservations/batch-cancel-confirmed",
            reservationIds,
            Void::class.java
        )
    }

    @CircuitBreaker(name = "inventory-command")
    override fun batchReserveStock(items: List<Pair<Long, Int>>): Map<Long, String> {
        val requestBody = items.map { BatchReserveRequest(it.first, it.second) }
        val requestKey = UUID.randomUUID().toString()
        val request = org.springframework.http.HttpEntity(requestBody, reserveHeaders(requestKey))
        val response = try {
            exchangeBatchReserve(request)
        } catch (e: ResourceAccessException) {
            logger.warn("배치 재고 예약 I/O 오류, 같은 요청 키로 한 번 다시 보냄: requestKey={}, cause={}", requestKey, e.message)
            exchangeBatchReserve(request)
        }
        return response.body ?: throw RuntimeException("배치 재고 예약 응답 없음")
    }

    private fun exchangeBatchReserve(request: org.springframework.http.HttpEntity<*>) =
        restTemplate.exchange(
            "$productServiceUrl/internal/api/v1/inventory/batch-reserve",
            org.springframework.http.HttpMethod.POST,
            request,
            object : org.springframework.core.ParameterizedTypeReference<Map<Long, String>>() {}
        )

    private fun reserveHeaders(requestKey: String): org.springframework.http.HttpHeaders {
        val headers = org.springframework.http.HttpHeaders()
        headers.set(REQUEST_KEY_HEADER, requestKey)
        if (budgetHeaderEnabled) headers.set(RESERVE_BUDGET_HEADER, RESERVE_BUDGET_MS.toString())
        return headers
    }

    companion object {
        const val RESERVE_BUDGET_HEADER = "X-Reserve-Budget-Ms"
        const val RESERVE_BUDGET_MS = 5000L
        const val RESPONSE_MARGIN_MS = 1000L
        val READ_TIMEOUT: Duration = Duration.ofMillis(RESERVE_BUDGET_MS + RESPONSE_MARGIN_MS)
        const val REQUEST_KEY_HEADER = "X-Reservation-Request-Key"
    }

    data class ReservationResponse(val reservationId: String = "")
    data class ConfirmationResponse(val confirmed: Boolean = false)
    data class BatchReserveRequest(val productId: Long, val quantity: Int)
}
