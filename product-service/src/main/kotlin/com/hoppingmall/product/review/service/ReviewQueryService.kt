package com.hoppingmall.product.review.service

import com.hoppingmall.product.review.dto.response.ReviewResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice

interface ReviewQueryService {
    fun getReview(reviewId: Long): ReviewResponse
    fun getReviewsByProductId(productId: Long, pageable: Pageable): Slice<ReviewResponse>
    fun getMyReviews(buyerId: Long, pageable: Pageable): Slice<ReviewResponse>
}
