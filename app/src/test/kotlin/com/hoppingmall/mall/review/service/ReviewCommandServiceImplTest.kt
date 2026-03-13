package com.hoppingmall.mall.review.service

import com.hoppingmall.mall.order.api.OrderItemInfo
import com.hoppingmall.mall.order.api.OrderQueryPort
import com.hoppingmall.mall.product.domain.ProductStatistics
import com.hoppingmall.mall.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.mall.review.domain.Review
import com.hoppingmall.mall.review.domain.repository.ReviewRepository
import com.hoppingmall.mall.review.dto.request.ReviewCreateRequest
import com.hoppingmall.mall.review.dto.request.ReviewUpdateRequest
import com.hoppingmall.mall.review.exception.ReviewAccessDeniedException
import com.hoppingmall.mall.review.exception.ReviewAlreadyExistsException
import com.hoppingmall.mall.review.exception.ReviewException
import com.hoppingmall.mall.review.exception.ReviewNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.math.BigDecimal

@DisplayName("ReviewCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ReviewCommandServiceImplTest {

    @Mock
    private lateinit var reviewRepository: ReviewRepository

    @Mock
    private lateinit var orderQueryPort: OrderQueryPort

    @Mock
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    private lateinit var reviewCommandService: ReviewCommandServiceImpl

    @BeforeEach
    fun setUp() {
        reviewCommandService = ReviewCommandServiceImpl(
            reviewRepository,
            orderQueryPort,
            productStatisticsRepository
        )
    }

    private fun orderItemInfo(
        id: Long = 1L,
        orderId: Long = 1L,
        sellerId: Long = 1L,
        productId: Long = 100L,
        productName: String = "테스트 상품",
        productPrice: BigDecimal = BigDecimal("10000"),
        quantity: Int = 1,
        totalPrice: BigDecimal = BigDecimal("10000")
    ) = OrderItemInfo(id, orderId, sellerId, productId, productName, productPrice, quantity, totalPrice)

    @Nested
    @DisplayName("createReview")
    inner class CreateReview {

        @Test
        fun 리뷰_생성_성공() {
            val buyerId = 1L
            val request = ReviewCreateRequest(
                orderItemId = 1L,
                rating = 5,
                content = "정말 좋은 상품입니다. 배송도 빠르고 품질도 만족합니다."
            )

            whenever(orderQueryPort.findOrderItemById(1L)).thenReturn(orderItemInfo())
            whenever(orderQueryPort.isDelivered(1L, buyerId)).thenReturn(true)
            whenever(reviewRepository.existsByOrderItemId(1L)).thenReturn(false)
            whenever(reviewRepository.save(any<Review>())).thenAnswer { invocation ->
                invocation.getArgument<Review>(0).withId(1L)
            }
            whenever(productStatisticsRepository.findByProductIdForUpdate(100L)).thenReturn(null)

            val result = reviewCommandService.createReview(buyerId, request)

            assertThat(result.rating).isEqualTo(5)
            assertThat(result.productId).isEqualTo(100L)
            assertThat(result.buyerId).isEqualTo(buyerId)
            verify(reviewRepository).save(any())
        }

        @Test
        fun 존재하지_않는_주문_상품으로_리뷰_생성_시_예외_발생() {
            val buyerId = 1L
            val request = ReviewCreateRequest(
                orderItemId = 999L,
                rating = 5,
                content = "정말 좋은 상품입니다. 배송도 빠르고 품질도 만족합니다."
            )

            whenever(orderQueryPort.findOrderItemById(999L)).thenReturn(null)

            assertThatThrownBy { reviewCommandService.createReview(buyerId, request) }
                .isInstanceOf(ReviewException::class.java)
        }

        @Test
        fun 배송_완료되지_않은_주문에_리뷰_생성_시_예외_발생() {
            val buyerId = 1L
            val request = ReviewCreateRequest(
                orderItemId = 1L,
                rating = 5,
                content = "정말 좋은 상품입니다. 배송도 빠르고 품질도 만족합니다."
            )

            whenever(orderQueryPort.findOrderItemById(1L)).thenReturn(orderItemInfo())
            whenever(orderQueryPort.isDelivered(1L, buyerId)).thenReturn(false)

            assertThatThrownBy { reviewCommandService.createReview(buyerId, request) }
                .isInstanceOf(ReviewException::class.java)
        }

        @Test
        fun 다른_구매자의_주문에_리뷰_생성_시_예외_발생() {
            val buyerId = 2L
            val request = ReviewCreateRequest(
                orderItemId = 1L,
                rating = 5,
                content = "정말 좋은 상품입니다. 배송도 빠르고 품질도 만족합니다."
            )

            whenever(orderQueryPort.findOrderItemById(1L)).thenReturn(orderItemInfo())
            whenever(orderQueryPort.isDelivered(1L, buyerId)).thenReturn(false)

            assertThatThrownBy { reviewCommandService.createReview(buyerId, request) }
                .isInstanceOf(ReviewException::class.java)
        }

        @Test
        fun 이미_리뷰가_존재하는_주문_상품에_리뷰_생성_시_예외_발생() {
            val buyerId = 1L
            val request = ReviewCreateRequest(
                orderItemId = 1L,
                rating = 5,
                content = "정말 좋은 상품입니다. 배송도 빠르고 품질도 만족합니다."
            )

            whenever(orderQueryPort.findOrderItemById(1L)).thenReturn(orderItemInfo())
            whenever(orderQueryPort.isDelivered(1L, buyerId)).thenReturn(true)
            whenever(reviewRepository.existsByOrderItemId(1L)).thenReturn(true)

            assertThatThrownBy { reviewCommandService.createReview(buyerId, request) }
                .isInstanceOf(ReviewAlreadyExistsException::class.java)
        }

        @Test
        fun 리뷰_생성_시_상품_통계_업데이트() {
            val buyerId = 1L
            val stats = ProductStatistics.fixture(productId = 100L).withId(1L)
            val request = ReviewCreateRequest(
                orderItemId = 1L,
                rating = 4,
                content = "좋은 상품이에요. 만족스럽습니다. 추천합니다."
            )

            whenever(orderQueryPort.findOrderItemById(1L)).thenReturn(orderItemInfo())
            whenever(orderQueryPort.isDelivered(1L, buyerId)).thenReturn(true)
            whenever(reviewRepository.existsByOrderItemId(1L)).thenReturn(false)
            whenever(reviewRepository.save(any<Review>())).thenAnswer { invocation ->
                invocation.getArgument<Review>(0).withId(1L)
            }
            whenever(productStatisticsRepository.findByProductIdForUpdate(100L)).thenReturn(stats)
            whenever(reviewRepository.averageRatingByProductId(100L)).thenReturn(4.0)
            whenever(reviewRepository.countByProductId(100L)).thenReturn(1L)

            reviewCommandService.createReview(buyerId, request)

            verify(productStatisticsRepository).findByProductIdForUpdate(100L)
            verify(reviewRepository).averageRatingByProductId(100L)
            verify(reviewRepository).countByProductId(100L)
        }
    }

    @Nested
    @DisplayName("updateReview")
    inner class UpdateReview {

        @Test
        fun 리뷰_수정_성공() {
            val buyerId = 1L
            val review = Review.fixture(buyerId = buyerId, productId = 100L).withId(1L)
            val request = ReviewUpdateRequest(
                rating = 3,
                content = "다시 생각해보니 보통인 것 같습니다. 괜찮은 수준이에요."
            )

            whenever(reviewRepository.findNullableById(1L)).thenReturn(review)
            whenever(productStatisticsRepository.findByProductIdForUpdate(100L)).thenReturn(null)

            val result = reviewCommandService.updateReview(1L, buyerId, request)

            assertThat(result.rating).isEqualTo(3)
            assertThat(result.content).isEqualTo("다시 생각해보니 보통인 것 같습니다. 괜찮은 수준이에요.")
        }

        @Test
        fun 존재하지_않는_리뷰_수정_시_예외_발생() {
            val request = ReviewUpdateRequest(
                rating = 3,
                content = "다시 생각해보니 보통인 것 같습니다. 괜찮은 수준이에요."
            )

            whenever(reviewRepository.findNullableById(999L)).thenReturn(null)

            assertThatThrownBy { reviewCommandService.updateReview(999L, 1L, request) }
                .isInstanceOf(ReviewNotFoundException::class.java)
        }

        @Test
        fun 다른_사용자의_리뷰_수정_시_예외_발생() {
            val review = Review.fixture(buyerId = 1L).withId(1L)
            val request = ReviewUpdateRequest(
                rating = 3,
                content = "다시 생각해보니 보통인 것 같습니다. 괜찮은 수준이에요."
            )

            whenever(reviewRepository.findNullableById(1L)).thenReturn(review)

            assertThatThrownBy { reviewCommandService.updateReview(1L, 2L, request) }
                .isInstanceOf(ReviewAccessDeniedException::class.java)
        }
    }

    @Nested
    @DisplayName("deleteReview")
    inner class DeleteReview {

        @Test
        fun 리뷰_삭제_성공() {
            val buyerId = 1L
            val review = Review.fixture(buyerId = buyerId, productId = 100L).withId(1L)

            whenever(reviewRepository.findNullableById(1L)).thenReturn(review)
            whenever(productStatisticsRepository.findByProductIdForUpdate(100L)).thenReturn(null)

            reviewCommandService.deleteReview(1L, buyerId)

            assertThat(review.deletedAt).isNotNull()
        }

        @Test
        fun 존재하지_않는_리뷰_삭제_시_예외_발생() {
            whenever(reviewRepository.findNullableById(999L)).thenReturn(null)

            assertThatThrownBy { reviewCommandService.deleteReview(999L, 1L) }
                .isInstanceOf(ReviewNotFoundException::class.java)
        }

        @Test
        fun 다른_사용자의_리뷰_삭제_시_예외_발생() {
            val review = Review.fixture(buyerId = 1L).withId(1L)

            whenever(reviewRepository.findNullableById(1L)).thenReturn(review)

            assertThatThrownBy { reviewCommandService.deleteReview(1L, 2L) }
                .isInstanceOf(ReviewAccessDeniedException::class.java)
        }
    }
}
