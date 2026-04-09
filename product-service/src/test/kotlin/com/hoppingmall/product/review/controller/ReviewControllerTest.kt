package com.hoppingmall.product.review.controller

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.product.review.dto.request.ReviewCreateRequest
import com.hoppingmall.product.review.dto.request.ReviewUpdateRequest
import com.hoppingmall.product.review.dto.response.ReviewResponse
import com.hoppingmall.product.review.service.ReviewCommandService
import com.hoppingmall.product.review.service.ReviewQueryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

@DisplayName("ReviewController")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ReviewControllerTest {

    @Mock
    private lateinit var reviewCommandService: ReviewCommandService

    @Mock
    private lateinit var reviewQueryService: ReviewQueryService

    @InjectMocks
    private lateinit var controller: ReviewController

    private val principal = UserPrincipal(1L, "BUYER")
    private val now = LocalDateTime.now()

    private fun reviewResponse() = ReviewResponse(
        id = 1L, buyerId = 1L, orderItemId = 100L, productId = 1L,
        rating = 5, content = "좋아요", imageUrl = null,
        createdAt = now, updatedAt = now
    )

    @Test
    fun 리뷰를_생성한다() {
        val request = ReviewCreateRequest(orderItemId = 100L, rating = 5, content = "좋아요")

        whenever(reviewCommandService.createReview(eq(1L), any())).thenReturn(reviewResponse())

        val result = controller.createReview(principal, request)

        assertThat(result.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(result.body!!.data!!.rating).isEqualTo(5)
    }

    @Test
    fun 리뷰를_단건_조회한다() {
        whenever(reviewQueryService.getReview(1L)).thenReturn(reviewResponse())

        val result = controller.getReview(1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 상품별_리뷰를_조회한다() {
        val pageable = PageRequest.of(0, 10)

        whenever(reviewQueryService.getReviewsByProductId(1L, pageable))
            .thenReturn(SliceImpl(listOf(reviewResponse()), pageable, false))

        val result = controller.getProductReviews(1L, pageable)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 내_리뷰를_조회한다() {
        val pageable = PageRequest.of(0, 10)

        whenever(reviewQueryService.getMyReviews(1L, pageable))
            .thenReturn(SliceImpl(listOf(reviewResponse()), pageable, false))

        val result = controller.getMyReviews(principal, pageable)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 리뷰를_수정한다() {
        val request = ReviewUpdateRequest(rating = 3, content = "보통", imageUrl = null)

        whenever(reviewCommandService.updateReview(eq(1L), eq(1L), any())).thenReturn(reviewResponse())

        val result = controller.updateReview(principal, 1L, request)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 리뷰를_삭제한다() {
        val result = controller.deleteReview(principal, 1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        verify(reviewCommandService).deleteReview(1L, 1L)
    }
}
