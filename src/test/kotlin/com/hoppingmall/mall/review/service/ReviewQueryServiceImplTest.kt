package com.hoppingmall.mall.review.service

import com.hoppingmall.mall.review.domain.Review
import com.hoppingmall.mall.review.domain.repository.ReviewRepository
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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

@DisplayName("ReviewQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ReviewQueryServiceImplTest {

    @Mock
    private lateinit var reviewRepository: ReviewRepository

    private lateinit var reviewQueryService: ReviewQueryServiceImpl

    @BeforeEach
    fun setUp() {
        reviewQueryService = ReviewQueryServiceImpl(reviewRepository)
    }

    @Nested
    @DisplayName("getReview")
    inner class GetReview {

        @Test
        fun 리뷰_단건_조회_성공() {
            // Data
            val review = Review.fixture().withId(1L)

            // Context
            whenever(reviewRepository.findNullableById(1L)).thenReturn(review)

            // Interaction
            val result = reviewQueryService.getReview(1L)

            // Assertions
            assertThat(result.id).isEqualTo(1L)
            assertThat(result.rating).isEqualTo(5)
            verify(reviewRepository).findNullableById(1L)
        }

        @Test
        fun 존재하지_않는_리뷰_조회_시_예외_발생() {
            // Context
            whenever(reviewRepository.findNullableById(999L)).thenReturn(null)

            // Interaction & Assertions
            assertThatThrownBy { reviewQueryService.getReview(999L) }
                .isInstanceOf(ReviewNotFoundException::class.java)
        }
    }

    @Nested
    @DisplayName("getReviewsByProductId")
    inner class GetReviewsByProductId {

        @Test
        fun 상품별_리뷰_목록_조회_성공() {
            // Data
            val productId = 100L
            val pageable = PageRequest.of(0, 10)
            val reviews = listOf(
                Review.fixture(productId = productId, rating = 5).withId(1L),
                Review.fixture(productId = productId, rating = 4, orderItemId = 2L).withId(2L)
            )

            // Context
            whenever(reviewRepository.findByProductId(productId, pageable))
                .thenReturn(PageImpl(reviews, pageable, 2))

            // Interaction
            val result = reviewQueryService.getReviewsByProductId(productId, pageable)

            // Assertions
            assertThat(result.content).hasSize(2)
            assertThat(result.content[0].rating).isEqualTo(5)
            assertThat(result.content[1].rating).isEqualTo(4)
            assertThat(result.totalElements).isEqualTo(2)
            verify(reviewRepository).findByProductId(productId, pageable)
        }

        @Test
        fun 빈_리뷰_목록_조회_성공() {
            // Data
            val productId = 100L
            val pageable = PageRequest.of(0, 10)

            // Context
            whenever(reviewRepository.findByProductId(productId, pageable))
                .thenReturn(PageImpl(emptyList(), pageable, 0))

            // Interaction
            val result = reviewQueryService.getReviewsByProductId(productId, pageable)

            // Assertions
            assertThat(result.content).isEmpty()
            assertThat(result.totalElements).isEqualTo(0)
            verify(reviewRepository).findByProductId(productId, pageable)
        }
    }

    @Nested
    @DisplayName("getMyReviews")
    inner class GetMyReviews {

        @Test
        fun 내_리뷰_목록_조회_성공() {
            // Data
            val buyerId = 1L
            val pageable = PageRequest.of(0, 10)
            val reviews = listOf(
                Review.fixture(buyerId = buyerId, productId = 100L).withId(1L),
                Review.fixture(buyerId = buyerId, productId = 200L, orderItemId = 2L).withId(2L)
            )

            // Context
            whenever(reviewRepository.findByBuyerId(buyerId, pageable))
                .thenReturn(PageImpl(reviews, pageable, 2))

            // Interaction
            val result = reviewQueryService.getMyReviews(buyerId, pageable)

            // Assertions
            assertThat(result.content).hasSize(2)
            assertThat(result.totalElements).isEqualTo(2)
            verify(reviewRepository).findByBuyerId(buyerId, pageable)
        }
    }
}
