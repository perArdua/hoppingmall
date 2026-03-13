package com.hoppingmall.mall.review.service

import com.hoppingmall.mall.review.dto.response.ReviewResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ReviewQueryService {
    fun getReview(reviewId: Long): ReviewResponse
    fun getReviewsByProductId(productId: Long, pageable: Pageable): Page<ReviewResponse>
    fun getMyReviews(buyerId: Long, pageable: Pageable): Page<ReviewResponse>
}
