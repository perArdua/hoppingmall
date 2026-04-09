package com.hoppingmall.product.review.service

import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.review.domain.Review
import com.hoppingmall.product.review.domain.repository.ReviewRepository
import com.hoppingmall.product.review.dto.request.ReviewCreateRequest
import com.hoppingmall.product.review.dto.request.ReviewUpdateRequest
import com.hoppingmall.product.review.exception.ReviewAccessDeniedException
import com.hoppingmall.product.review.exception.ReviewAlreadyExistsException
import com.hoppingmall.product.review.exception.ReviewException
import com.hoppingmall.product.review.exception.ReviewNotFoundException
import com.hoppingmall.product.review.port.OrderItemInfo
import com.hoppingmall.product.review.port.OrderQueryPort
import com.hoppingmall.product.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@DisplayName("ReviewCommandServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ReviewCommandServiceImplTest {

    @Mock
    private lateinit var reviewRepository: ReviewRepository

    @Mock
    private lateinit var orderQueryPort: OrderQueryPort

    @Mock
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @InjectMocks
    private lateinit var service: ReviewCommandServiceImpl

    private val orderItemInfo = OrderItemInfo(
        id = 100L, orderId = 10L, sellerId = 1L, productId = 1L,
        productName = "테스트", productPrice = BigDecimal("10000"),
        quantity = 1, totalPrice = BigDecimal("10000")
    )

    @Test
    fun 리뷰를_생성한다() {
        val request = ReviewCreateRequest(orderItemId = 100L, rating = 5, content = "좋아요")
        val review = Review.create(
            buyerId = 1L, orderItemId = 100L, productId = 1L,
            rating = 5, content = "좋아요"
        ).withId(1L)

        whenever(orderQueryPort.findOrderItemById(100L)).thenReturn(orderItemInfo)
        whenever(orderQueryPort.isDelivered(10L, 1L)).thenReturn(true)
        whenever(reviewRepository.existsByOrderItemId(100L)).thenReturn(false)
        whenever(reviewRepository.save(any<Review>())).thenReturn(review)
        whenever(productStatisticsRepository.findByProductIdForUpdate(1L)).thenReturn(null)

        val result = service.createReview(1L, request)

        assertThat(result.rating).isEqualTo(5)
    }

    @Test
    fun 주문_상품이_없으면_리뷰_생성_시_예외를_발생시킨다() {
        val request = ReviewCreateRequest(orderItemId = 999L, rating = 5, content = "좋아요")

        whenever(orderQueryPort.findOrderItemById(999L)).thenReturn(null)

        assertThatThrownBy { service.createReview(1L, request) }
            .isInstanceOf(ReviewException::class.java)
    }

    @Test
    fun 배송_미완료_시_리뷰_생성_예외를_발생시킨다() {
        val request = ReviewCreateRequest(orderItemId = 100L, rating = 5, content = "좋아요")

        whenever(orderQueryPort.findOrderItemById(100L)).thenReturn(orderItemInfo)
        whenever(orderQueryPort.isDelivered(10L, 1L)).thenReturn(false)

        assertThatThrownBy { service.createReview(1L, request) }
            .isInstanceOf(ReviewException::class.java)
    }

    @Test
    fun 이미_리뷰가_존재하면_예외를_발생시킨다() {
        val request = ReviewCreateRequest(orderItemId = 100L, rating = 5, content = "좋아요")

        whenever(orderQueryPort.findOrderItemById(100L)).thenReturn(orderItemInfo)
        whenever(orderQueryPort.isDelivered(10L, 1L)).thenReturn(true)
        whenever(reviewRepository.existsByOrderItemId(100L)).thenReturn(true)

        assertThatThrownBy { service.createReview(1L, request) }
            .isInstanceOf(ReviewAlreadyExistsException::class.java)
    }

    @Test
    fun 리뷰를_수정한다() {
        val review = Review.create(
            buyerId = 1L, orderItemId = 100L, productId = 1L,
            rating = 5, content = "좋아요"
        ).withId(1L)
        val request = ReviewUpdateRequest(rating = 3, content = "보통", imageUrl = null)

        whenever(reviewRepository.findNullableById(1L)).thenReturn(review)
        whenever(productStatisticsRepository.findByProductIdForUpdate(1L)).thenReturn(null)

        val result = service.updateReview(1L, 1L, request)

        assertThat(result.rating).isEqualTo(3)
    }

    @Test
    fun 존재하지_않는_리뷰_수정_시_예외를_발생시킨다() {
        val request = ReviewUpdateRequest(rating = 3, content = "보통", imageUrl = null)

        whenever(reviewRepository.findNullableById(999L)).thenReturn(null)

        assertThatThrownBy { service.updateReview(999L, 1L, request) }
            .isInstanceOf(ReviewNotFoundException::class.java)
    }

    @Test
    fun 다른_사용자의_리뷰_수정_시_예외를_발생시킨다() {
        val review = Review.create(
            buyerId = 1L, orderItemId = 100L, productId = 1L,
            rating = 5, content = "좋아요"
        ).withId(1L)
        val request = ReviewUpdateRequest(rating = 3, content = "보통", imageUrl = null)

        whenever(reviewRepository.findNullableById(1L)).thenReturn(review)

        assertThatThrownBy { service.updateReview(1L, 999L, request) }
            .isInstanceOf(ReviewAccessDeniedException::class.java)
    }

    @Test
    fun 리뷰를_삭제한다() {
        val review = Review.create(
            buyerId = 1L, orderItemId = 100L, productId = 1L,
            rating = 5, content = "좋아요"
        ).withId(1L)

        whenever(reviewRepository.findNullableById(1L)).thenReturn(review)
        whenever(productStatisticsRepository.findByProductIdForUpdate(1L)).thenReturn(null)

        service.deleteReview(1L, 1L)

        assertThat(review.deletedAt).isNotNull()
    }

    @Test
    fun 존재하지_않는_리뷰_삭제_시_예외를_발생시킨다() {
        whenever(reviewRepository.findNullableById(999L)).thenReturn(null)

        assertThatThrownBy { service.deleteReview(999L, 1L) }
            .isInstanceOf(ReviewNotFoundException::class.java)
    }

    @Test
    fun 다른_사용자의_리뷰_삭제_시_예외를_발생시킨다() {
        val review = Review.create(
            buyerId = 1L, orderItemId = 100L, productId = 1L,
            rating = 5, content = "좋아요"
        ).withId(1L)

        whenever(reviewRepository.findNullableById(1L)).thenReturn(review)

        assertThatThrownBy { service.deleteReview(1L, 999L) }
            .isInstanceOf(ReviewAccessDeniedException::class.java)
    }

    @Test
    fun 리뷰_생성_후_통계를_업데이트한다() {
        val request = ReviewCreateRequest(orderItemId = 100L, rating = 5, content = "좋아요")
        val review = Review.create(
            buyerId = 1L, orderItemId = 100L, productId = 1L,
            rating = 5, content = "좋아요"
        ).withId(1L)
        val stats = ProductStatistics.create(
            productId = 1L, productName = "테스트", sellerId = 1L, categoryId = 1L
        ).withId(1L)

        whenever(orderQueryPort.findOrderItemById(100L)).thenReturn(orderItemInfo)
        whenever(orderQueryPort.isDelivered(10L, 1L)).thenReturn(true)
        whenever(reviewRepository.existsByOrderItemId(100L)).thenReturn(false)
        whenever(reviewRepository.save(any<Review>())).thenReturn(review)
        whenever(productStatisticsRepository.findByProductIdForUpdate(1L)).thenReturn(stats)
        whenever(reviewRepository.averageRatingByProductId(1L)).thenReturn(4.5)
        whenever(reviewRepository.countByProductId(1L)).thenReturn(10)

        service.createReview(1L, request)

        assertThat(stats.averageRating).isEqualByComparingTo(BigDecimal("4.50"))
        assertThat(stats.reviewCount).isEqualTo(10)
    }
}
