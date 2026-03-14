package com.hoppingmall.product.review.controller

import com.hoppingmall.product.common.UserPrincipal
import com.hoppingmall.product.common.ApiResponse
import com.hoppingmall.product.common.idempotency.Idempotent
import com.hoppingmall.product.review.dto.request.ReviewCreateRequest
import com.hoppingmall.product.review.dto.request.ReviewUpdateRequest
import com.hoppingmall.product.review.dto.response.ReviewResponse
import com.hoppingmall.product.review.service.ReviewCommandService
import com.hoppingmall.product.review.service.ReviewQueryService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
@Tag(name = "리뷰")
class ReviewController(
    private val reviewCommandService: ReviewCommandService,
    private val reviewQueryService: ReviewQueryService
) {

    @Idempotent(ttlHours = 24)
    @PostMapping("/reviews")
    fun createReview(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @Valid @RequestBody request: ReviewCreateRequest
    ): ResponseEntity<ApiResponse<ReviewResponse>> {
        val response = reviewCommandService.createReview(userPrincipal.getUserId(), request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response))
    }

    @GetMapping("/reviews/{reviewId}")
    fun getReview(
        @PathVariable reviewId: Long
    ): ResponseEntity<ApiResponse<ReviewResponse>> {
        val response = reviewQueryService.getReview(reviewId)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @GetMapping("/products/{productId}/reviews")
    fun getProductReviews(
        @PathVariable productId: Long,
        @PageableDefault(size = 10) pageable: Pageable
    ): ResponseEntity<ApiResponse<Page<ReviewResponse>>> {
        val response = reviewQueryService.getReviewsByProductId(productId, pageable)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @GetMapping("/reviews/my")
    fun getMyReviews(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PageableDefault(size = 10) pageable: Pageable
    ): ResponseEntity<ApiResponse<Page<ReviewResponse>>> {
        val response = reviewQueryService.getMyReviews(userPrincipal.getUserId(), pageable)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @Idempotent(ttlHours = 24)
    @PutMapping("/reviews/{reviewId}")
    fun updateReview(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PathVariable reviewId: Long,
        @Valid @RequestBody request: ReviewUpdateRequest
    ): ResponseEntity<ApiResponse<ReviewResponse>> {
        val response = reviewCommandService.updateReview(reviewId, userPrincipal.getUserId(), request)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @Idempotent(ttlHours = 24)
    @DeleteMapping("/reviews/{reviewId}")
    fun deleteReview(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PathVariable reviewId: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        reviewCommandService.deleteReview(reviewId, userPrincipal.getUserId())
        return ResponseEntity.ok(ApiResponse.success(Unit))
    }
}
