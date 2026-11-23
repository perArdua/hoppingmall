package com.hoppingmall.product.internal

import com.hoppingmall.product.review.service.ProductReviewSummaryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/api/v1/products/review-summary")
class InternalProductReviewSummaryController(
    private val productReviewSummaryService: ProductReviewSummaryService
) {

    @PostMapping("/backfill")
    fun backfill(): ResponseEntity<ProductReviewSummaryService.BackfillResult> =
        ResponseEntity.ok(productReviewSummaryService.backfill())
}
