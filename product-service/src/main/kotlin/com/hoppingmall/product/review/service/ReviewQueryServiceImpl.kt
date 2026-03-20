package com.hoppingmall.product.review.service

import com.hoppingmall.product.review.domain.repository.ReviewRepository
import com.hoppingmall.product.review.dto.response.ReviewResponse
import com.hoppingmall.product.review.exception.ReviewNotFoundException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
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

    override fun getReviewsByProductId(productId: Long, pageable: Pageable): Slice<ReviewResponse> {
        return reviewRepository.findByProductId(productId, pageable)
            .map { ReviewResponse.from(it) }
    }

    override fun getMyReviews(buyerId: Long, pageable: Pageable): Slice<ReviewResponse> {
        return reviewRepository.findByBuyerId(buyerId, pageable)
            .map { ReviewResponse.from(it) }
    }
}
