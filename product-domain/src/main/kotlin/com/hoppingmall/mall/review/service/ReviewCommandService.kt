package com.hoppingmall.mall.review.service

import com.hoppingmall.mall.review.dto.request.ReviewCreateRequest
import com.hoppingmall.mall.review.dto.request.ReviewUpdateRequest
import com.hoppingmall.mall.review.dto.response.ReviewResponse

interface ReviewCommandService {
    fun createReview(buyerId: Long, request: ReviewCreateRequest): ReviewResponse
    fun updateReview(reviewId: Long, buyerId: Long, request: ReviewUpdateRequest): ReviewResponse
    fun deleteReview(reviewId: Long, buyerId: Long)
}
