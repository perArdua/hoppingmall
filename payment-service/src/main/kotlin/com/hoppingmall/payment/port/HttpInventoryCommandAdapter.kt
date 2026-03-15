package com.hoppingmall.payment.port

import com.hoppingmall.payment.port.exception.InventoryRestoreFailedException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class HttpInventoryCommandAdapter(
    private val productServiceUrl: String,
    private val restTemplate: RestTemplate
) : InventoryCommandPort {

    @Autowired
    constructor(
        @Value("\${services.product-service.url:http://localhost:8083}") productServiceUrl: String,
        restTemplateBuilder: RestTemplateBuilder
    ) : this(
        productServiceUrl,
        restTemplateBuilder.connectTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofSeconds(5)).build()
    )

    private val logger = LoggerFactory.getLogger(HttpInventoryCommandAdapter::class.java)

    override fun increaseStock(productId: Long, quantity: Int) {
        try {
            restTemplate.postForEntity(
                "$productServiceUrl/internal/api/v1/inventory/$productId/increase?quantity=$quantity",
                null,
                Void::class.java
            )
        } catch (e: Exception) {
            logger.error("재고 복구 실패: productId=$productId, quantity=$quantity", e)
            throw InventoryRestoreFailedException(productId, quantity, e)
        }
    }
}
