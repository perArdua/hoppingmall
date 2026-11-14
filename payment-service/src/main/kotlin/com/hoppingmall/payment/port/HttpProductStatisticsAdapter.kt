package com.hoppingmall.payment.port

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.Duration

@Component
class HttpProductStatisticsAdapter(
    @Value("\${services.product-service.url:http://localhost:8083}") private val productServiceUrl: String,
    restTemplateBuilder: RestTemplateBuilder
) : ProductStatisticsPort {

    private val logger = LoggerFactory.getLogger(HttpProductStatisticsAdapter::class.java)

    private val restTemplate: RestTemplate = restTemplateBuilder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .build()

    @CircuitBreaker(name = "product-statistics-command")
    override fun incrementRefundStats(productId: Long, quantity: Long, amount: BigDecimal) {
        post(productId, quantity, amount, null, null)
    }

    @CircuitBreaker(name = "product-statistics-command")
    override fun incrementRefundStats(productId: Long, quantity: Long, amount: BigDecimal, requestKey: String) {
        post(productId, quantity, amount, null, requestKey)
    }

    @CircuitBreaker(name = "product-statistics-command")
    override fun incrementRefundStats(productId: Long, quantity: Long, amount: BigDecimal, productPrice: BigDecimal) {
        post(productId, quantity, amount, productPrice, null)
    }

    @CircuitBreaker(name = "product-statistics-command")
    override fun incrementRefundStats(
        productId: Long,
        quantity: Long,
        amount: BigDecimal,
        productPrice: BigDecimal,
        requestKey: String
    ) {
        post(productId, quantity, amount, productPrice, requestKey)
    }

    private fun post(productId: Long, quantity: Long, amount: BigDecimal, productPrice: BigDecimal?, requestKey: String?) {
        restTemplate.postForEntity(
            "$productServiceUrl/internal/api/v1/product-statistics/$productId/refund?quantity=$quantity&amount=$amount",
            refundStatsEntity(productPrice, requestKey),
            Void::class.java
        )
    }

    private fun refundStatsEntity(productPrice: BigDecimal?, requestKey: String?): HttpEntity<RefundStatsRequest> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        requestKey?.let { headers.set(REQUEST_KEY_HEADER, it) }
        return HttpEntity(RefundStatsRequest(productPrice), headers)
    }

    data class RefundStatsRequest(val productPrice: BigDecimal?)
}
