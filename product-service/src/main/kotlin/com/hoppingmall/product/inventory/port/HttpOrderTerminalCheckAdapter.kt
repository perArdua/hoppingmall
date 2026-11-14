package com.hoppingmall.product.inventory.port

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class HttpOrderTerminalCheckAdapter(
    @Value("\${services.order-service.url:http://localhost:8084}") private val orderServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : OrderTerminalCheckPort {

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    override fun terminalReservationIds(reservationIds: List<String>): List<String> {
        val response = restTemplate.postForObject(
            "$orderServiceUrl$TERMINAL_CHECK_PATH",
            mapOf(REQUEST_FIELD to reservationIds),
            JsonNode::class.java
        )
        val ids = response?.path("data")?.path(RESPONSE_FIELD)
        if (response?.path("code")?.asText() != SUCCESS_CODE || ids == null || !ids.isArray || !ids.all { it.isTextual }) {
            throw IllegalStateException("terminal-check response does not match the contract")
        }
        return ids.map { it.asText() }
    }

    companion object {
        const val MAX_RESERVATION_IDS = 500
        private const val TERMINAL_CHECK_PATH = "/internal/api/v1/orders/terminal-check"
        private const val REQUEST_FIELD = "reservation_ids"
        private const val RESPONSE_FIELD = "terminal_reservation_ids"
        private const val SUCCESS_CODE = "SUCCESS"
    }
}
