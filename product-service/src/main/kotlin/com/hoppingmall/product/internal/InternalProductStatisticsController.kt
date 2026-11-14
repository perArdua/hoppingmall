package com.hoppingmall.product.internal

import com.hoppingmall.product.internal.enums.AppliedRequestOperation
import com.hoppingmall.product.internal.service.AppliedRequestKeyGuard
import com.hoppingmall.product.product.dto.request.RefundStatisticsRequest
import com.hoppingmall.product.product.service.ProductStatisticsCommandService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
        @RequestHeader(name = AppliedRequestKeyGuard.REQUEST_KEY_HEADER, required = false) requestKey: String? = null,
        @RequestBody(required = false) request: RefundStatisticsRequest? = null
    ): ResponseEntity<Void> {
        val refundAmount = request?.productPrice?.multiply(BigDecimal.valueOf(quantity)) ?: amount
        if (requestKey == null) {
            productStatisticsCommandService.incrementRefundStats(productId, quantity, refundAmount)
        } else {
            appliedRequestKeyGuard.applyOnce(AppliedRequestOperation.REFUND_STATISTICS, requestKey) {
                productStatisticsCommandService.incrementRefundStats(productId, quantity, refundAmount)
            }
        }
        return ResponseEntity.ok().build()
    }
}
