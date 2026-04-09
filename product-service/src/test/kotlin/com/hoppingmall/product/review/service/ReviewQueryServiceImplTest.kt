package com.hoppingmall.product.review.service

import com.hoppingmall.product.review.domain.Review
import com.hoppingmall.product.review.domain.repository.ReviewRepository
import com.hoppingmall.product.review.exception.ReviewNotFoundException
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
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl

@DisplayName("ReviewQueryServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ReviewQueryServiceImplTest {

    @Mock
    private lateinit var reviewRepository: ReviewRepository

    @InjectMocks
    private lateinit var service: ReviewQueryServiceImpl

    private fun createReview() = Review.create(
        buyerId = 1L, orderItemId = 100L, productId = 1L,
        rating = 5, content = "좋아요"
    ).withId(1L)

    @Test
    fun 리뷰를_단건_조회한다() {
        whenever(reviewRepository.findNullableById(1L)).thenReturn(createReview())

        val result = service.getReview(1L)

        assertThat(result.rating).isEqualTo(5)
    }

    @Test
    fun 존재하지_않는_리뷰_조회_시_예외를_발생시킨다() {
        whenever(reviewRepository.findNullableById(999L)).thenReturn(null)

        assertThatThrownBy { service.getReview(999L) }
            .isInstanceOf(ReviewNotFoundException::class.java)
    }

    @Test
    fun 상품별_리뷰를_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val review = createReview()

        whenever(reviewRepository.findByProductId(1L, pageable))
            .thenReturn(SliceImpl(listOf(review), pageable, false))

        val result = service.getReviewsByProductId(1L, pageable)

        assertThat(result.content).hasSize(1)
    }

    @Test
    fun 내_리뷰를_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val review = createReview()

        whenever(reviewRepository.findByBuyerId(1L, pageable))
            .thenReturn(SliceImpl(listOf(review), pageable, false))

        val result = service.getMyReviews(1L, pageable)

        assertThat(result.content).hasSize(1)
    }
}
