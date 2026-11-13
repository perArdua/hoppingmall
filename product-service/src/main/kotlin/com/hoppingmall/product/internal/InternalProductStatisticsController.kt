package com.hoppingmall.product.internal

import com.hoppingmall.product.internal.enums.AppliedRequestOperation
import com.hoppingmall.product.internal.service.AppliedRequestKeyGuard
import com.hoppingmall.product.product.service.ProductStatisticsCommandService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/internal/api/v1/product-statistics")
class InternalProductStatisticsController(
    private val productStatisticsCommandService: ProductStatisticsCommandService,
    private val appliedRequestKeyGuard: AppliedRequestKeyGuard
) {

    @PostMapping("/{productId}/refund")
    fun incrementRefundStats(
        @PathVariable productId: Long,
        @RequestParam quantity: Long,
        @RequestParam amount: BigDecimal,
        @RequestHeader(name = AppliedRequestKeyGuard.REQUEST_KEY_HEADER, required = false) requestKey: String? = null
    ): ResponseEntity<Void> {
        if (requestKey == null) {
            productStatisticsCommandService.incrementRefundStats(productId, quantity, amount)
        } else {
            appliedRequestKeyGuard.applyOnce(AppliedRequestOperation.REFUND_STATISTICS, requestKey) {
                productStatisticsCommandService.incrementRefundStats(productId, quantity, amount)
            }
        }
        return ResponseEntity.ok().build()
    }
}
