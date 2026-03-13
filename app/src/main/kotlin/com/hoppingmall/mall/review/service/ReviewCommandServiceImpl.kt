package com.hoppingmall.mall.review.service

import com.hoppingmall.mall.order.domain.repository.OrderItemRepository
import com.hoppingmall.mall.order.domain.repository.OrderRepository
import com.hoppingmall.mall.order.enum.OrderStatus
import com.hoppingmall.mall.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.mall.review.domain.Review
import com.hoppingmall.mall.review.domain.repository.ReviewRepository
import com.hoppingmall.mall.review.dto.request.ReviewCreateRequest
import com.hoppingmall.mall.review.dto.request.ReviewUpdateRequest
import com.hoppingmall.mall.review.dto.response.ReviewResponse
import com.hoppingmall.mall.review.exception.ReviewAccessDeniedException
import com.hoppingmall.mall.review.exception.ReviewAlreadyExistsException
import com.hoppingmall.mall.review.exception.ReviewException
import com.hoppingmall.mall.review.exception.ReviewNotFoundException
import com.hoppingmall.mall.review.exception.code.ReviewErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
@Transactional
class ReviewCommandServiceImpl(
    private val reviewRepository: ReviewRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val productStatisticsRepository: ProductStatisticsRepository
) : ReviewCommandService {

    override fun createReview(buyerId: Long, request: ReviewCreateRequest): ReviewResponse {
        val orderItem = orderItemRepository.findById(request.orderItemId)
            .orElseThrow { ReviewException(ReviewErrorCode.REVIEW_INVALID_ORDER_ITEM) }

        val order = orderRepository.findById(orderItem.orderId)
            .orElseThrow { ReviewException(ReviewErrorCode.REVIEW_INVALID_ORDER_ITEM) }

        if (order.status != OrderStatus.DELIVERED) {
            throw ReviewException(ReviewErrorCode.REVIEW_ORDER_NOT_DELIVERED)
        }

        if (order.buyerId != buyerId) {
            throw ReviewAccessDeniedException()
        }

        if (reviewRepository.existsByOrderItemId(request.orderItemId)) {
            throw ReviewAlreadyExistsException()
        }

        val review = Review.create(
            buyerId = buyerId,
            orderItemId = request.orderItemId,
            productId = orderItem.productId,
            rating = request.rating,
            content = request.content,
            imageUrl = request.imageUrl
        )
        val savedReview = reviewRepository.save(review)

        updateProductReviewStats(orderItem.productId)

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
