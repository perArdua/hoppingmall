package com.hoppingmall.payment.port

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class HttpOrderSummaryQueryAdapter(
    private val orderServiceUrl: String,
    private val restTemplate: RestTemplate
) : OrderSummaryQueryPort {

    @Autowired
    constructor(
        @Value("\${services.order-service.url:http://localhost:8084}") orderServiceUrl: String,
        restTemplateBuilder: RestTemplateBuilder
    ) : this(
        orderServiceUrl,
        restTemplateBuilder.connectTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofSeconds(5)).build()
    )

    override fun findOrder(orderId: Long): OrderSummary? = null
}
