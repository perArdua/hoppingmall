package com.hoppingmall.product.internal

import com.hoppingmall.product.product.service.ProductStatisticsCommandService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/internal/api/v1/product-statistics")
class InternalProductStatisticsController(
    private val productStatisticsCommandService: ProductStatisticsCommandService
) {

    @PostMapping("/{productId}/refund")
    fun incrementRefundStats(
        @PathVariable productId: Long,
        @RequestParam quantity: Long,
        @RequestParam amount: BigDecimal
    ): ResponseEntity<Void> {
        productStatisticsCommandService.incrementRefundStats(productId, quantity, amount)
        return ResponseEntity.ok().build()
    }
}
