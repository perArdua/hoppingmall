package com.hoppingmall.mall.review.service

import com.hoppingmall.mall.review.domain.repository.ReviewRepository
import com.hoppingmall.mall.review.dto.response.ReviewResponse
import com.hoppingmall.mall.review.exception.ReviewNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ReviewQueryServiceImpl(
    private val reviewRepository: ReviewRepository
) : ReviewQueryService {

    override fun getReview(reviewId: Long): ReviewResponse {
        val review = reviewRepository.findNullableById(reviewId)
            ?: throw ReviewNotFoundException()
        return ReviewResponse.from(review)
    }

    override fun getReviewsByProductId(productId: Long, pageable: Pageable): Page<ReviewResponse> {
        return reviewRepository.findByProductId(productId, pageable)
            .map { ReviewResponse.from(it) }
    }

    override fun getMyReviews(buyerId: Long, pageable: Pageable): Page<ReviewResponse> {
        return reviewRepository.findByBuyerId(buyerId, pageable)
            .map { ReviewResponse.from(it) }
    }
}
