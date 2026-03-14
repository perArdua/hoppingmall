package com.hoppingmall.product.review.service

import com.hoppingmall.product.review.port.OrderQueryPort
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.review.domain.Review
import com.hoppingmall.product.review.domain.repository.ReviewRepository
import com.hoppingmall.product.review.dto.request.ReviewCreateRequest
import com.hoppingmall.product.review.dto.request.ReviewUpdateRequest
import com.hoppingmall.product.review.dto.response.ReviewResponse
import com.hoppingmall.product.review.exception.ReviewAccessDeniedException
import com.hoppingmall.product.review.exception.ReviewAlreadyExistsException
import com.hoppingmall.product.review.exception.ReviewException
import com.hoppingmall.product.review.exception.ReviewNotFoundException
import com.hoppingmall.product.review.exception.code.ReviewErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
@Transactional
class ReviewCommandServiceImpl(
    private val reviewRepository: ReviewRepository,
    private val orderQueryPort: OrderQueryPort,
    private val productStatisticsRepository: ProductStatisticsRepository
) : ReviewCommandService {

    override fun createReview(buyerId: Long, request: ReviewCreateRequest): ReviewResponse {
        val orderItemInfo = orderQueryPort.findOrderItemById(request.orderItemId)
            ?: throw ReviewException(ReviewErrorCode.REVIEW_INVALID_ORDER_ITEM)

        if (!orderQueryPort.isDelivered(orderItemInfo.orderId, buyerId)) {
            throw ReviewException(ReviewErrorCode.REVIEW_ORDER_NOT_DELIVERED)
        }

        if (reviewRepository.existsByOrderItemId(request.orderItemId)) {
            throw ReviewAlreadyExistsException()
        }

        val review = Review.create(
            buyerId = buyerId,
            orderItemId = request.orderItemId,
            productId = orderItemInfo.productId,
            rating = request.rating,
            content = request.content,
            imageUrl = request.imageUrl
        )
        val savedReview = reviewRepository.save(review)

        updateProductReviewStats(orderItemInfo.productId)

        return ReviewResponse.from(savedReview)
    }

    override fun updateReview(reviewId: Long, buyerId: Long, request: ReviewUpdateRequest): ReviewResponse {
        val review = reviewRepository.findNullableById(reviewId)
            ?: throw ReviewNotFoundException()

        if (review.buyerId != buyerId) {
            throw ReviewAccessDeniedException()
        }

        review.update(
            rating = request.rating,
            content = request.content,
            imageUrl = request.imageUrl
        )

        updateProductReviewStats(review.productId)

        return ReviewResponse.from(review)
    }

    override fun deleteReview(reviewId: Long, buyerId: Long) {
        val review = reviewRepository.findNullableById(reviewId)
            ?: throw ReviewNotFoundException()

        if (review.buyerId != buyerId) {
            throw ReviewAccessDeniedException()
        }

        review.softDelete()

        updateProductReviewStats(review.productId)
    }

    private fun updateProductReviewStats(productId: Long) {
        val stats = productStatisticsRepository.findByProductIdForUpdate(productId) ?: return

        val avgRating = reviewRepository.averageRatingByProductId(productId)
            ?.let { BigDecimal(it).setScale(2, RoundingMode.HALF_UP) }
            ?: BigDecimal.ZERO
        val reviewCount = reviewRepository.countByProductId(productId)

        stats.updateReviewStats(avgRating, reviewCount)
    }
}
