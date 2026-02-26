package com.hoppingmall.mall.review.controller

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.review.dto.request.ReviewCreateRequest
import com.hoppingmall.mall.review.dto.request.ReviewUpdateRequest
import com.hoppingmall.mall.review.dto.response.ReviewResponse
import com.hoppingmall.mall.review.service.ReviewCommandService
import com.hoppingmall.mall.review.service.ReviewQueryService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.kotlin.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime

@DisplayName("ReviewController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ReviewControllerTest {

    private val reviewCommandService: ReviewCommandService = mock()
    private val reviewQueryService: ReviewQueryService = mock()
    private val controller = ReviewController(reviewCommandService, reviewQueryService)

    private fun createReviewResponse(
        id: Long = 1L,
        buyerId: Long = 1L,
        orderItemId: Long = 1L,
        productId: Long = 100L,
        rating: Int = 5,
        content: String = "정말 좋은 상품입니다. 배송도 빠르고 품질도 만족합니다."
    ): ReviewResponse {
        return ReviewResponse(
            id = id,
            buyerId = buyerId,
            orderItemId = orderItemId,
            productId = productId,
            rating = rating,
            content = content,
            imageUrl = null,
            createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
            updatedAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        )
    }

    @Nested
    @DisplayName("createReview")
    inner class CreateReview {
        @Test
        fun 리뷰_생성_성공() {
            // Data
            val userPrincipal = UserPrincipal(1L, "test@example.com", "BUYER")
            val request = ReviewCreateRequest(
                orderItemId = 1L,
                rating = 5,
                content = "정말 좋은 상품입니다. 배송도 빠르고 품질도 만족합니다."
            )
            val expectedResponse = createReviewResponse()

            // Context
            whenever(reviewCommandService.createReview(userPrincipal.getUserId(), request))
                .thenReturn(expectedResponse)

            // Interaction
            val response: ResponseEntity<ApiResponse<ReviewResponse>> =
                controller.createReview(userPrincipal, request)

            // Assertions
            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(reviewCommandService).createReview(userPrincipal.getUserId(), request)
        }
    }

    @Nested
    @DisplayName("getReview")
    inner class GetReview {
        @Test
        fun 리뷰_단건_조회_성공() {
            // Data
            val expectedResponse = createReviewResponse()

            // Context
            whenever(reviewQueryService.getReview(1L)).thenReturn(expectedResponse)

            // Interaction
            val response: ResponseEntity<ApiResponse<ReviewResponse>> = controller.getReview(1L)

            // Assertions
            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(reviewQueryService).getReview(1L)
        }
    }

    @Nested
    @DisplayName("getProductReviews")
    inner class GetProductReviews {
        @Test
        fun 상품별_리뷰_목록_조회_성공() {
            // Data
            val pageable = PageRequest.of(0, 10)
            val reviews = listOf(createReviewResponse(id = 1L), createReviewResponse(id = 2L, orderItemId = 2L))
            val expectedPage: Page<ReviewResponse> = PageImpl(reviews, pageable, 2)

            // Context
            whenever(reviewQueryService.getReviewsByProductId(100L, pageable)).thenReturn(expectedPage)

            // Interaction
            val response: ResponseEntity<ApiResponse<Page<ReviewResponse>>> =
                controller.getProductReviews(100L, pageable)

            // Assertions
            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedPage, response.body?.data)
            verify(reviewQueryService).getReviewsByProductId(100L, pageable)
        }
    }

    @Nested
    @DisplayName("getMyReviews")
    inner class GetMyReviews {
        @Test
        fun 내_리뷰_목록_조회_성공() {
            // Data
            val userPrincipal = UserPrincipal(1L, "test@example.com", "BUYER")
            val pageable = PageRequest.of(0, 10)
            val reviews = listOf(createReviewResponse())
            val expectedPage: Page<ReviewResponse> = PageImpl(reviews, pageable, 1)

            // Context
            whenever(reviewQueryService.getMyReviews(userPrincipal.getUserId(), pageable))
                .thenReturn(expectedPage)

            // Interaction
            val response: ResponseEntity<ApiResponse<Page<ReviewResponse>>> =
                controller.getMyReviews(userPrincipal, pageable)

            // Assertions
            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedPage, response.body?.data)
            verify(reviewQueryService).getMyReviews(userPrincipal.getUserId(), pageable)
        }
    }

    @Nested
    @DisplayName("updateReview")
    inner class UpdateReview {
        @Test
        fun 리뷰_수정_성공() {
            // Data
            val userPrincipal = UserPrincipal(1L, "test@example.com", "BUYER")
            val request = ReviewUpdateRequest(
                rating = 3,
                content = "다시 생각해보니 보통인 것 같습니다. 괜찮은 수준이에요."
            )
            val expectedResponse = createReviewResponse(rating = 3, content = "다시 생각해보니 보통인 것 같습니다. 괜찮은 수준이에요.")

            // Context
            whenever(reviewCommandService.updateReview(1L, userPrincipal.getUserId(), request))
                .thenReturn(expectedResponse)

            // Interaction
            val response: ResponseEntity<ApiResponse<ReviewResponse>> =
                controller.updateReview(userPrincipal, 1L, request)

            // Assertions
            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(expectedResponse, response.body?.data)
            verify(reviewCommandService).updateReview(1L, userPrincipal.getUserId(), request)
        }
    }

    @Nested
    @DisplayName("deleteReview")
    inner class DeleteReview {
        @Test
        fun 리뷰_삭제_성공() {
            // Data
            val userPrincipal = UserPrincipal(1L, "test@example.com", "BUYER")

            // Context
            doNothing().whenever(reviewCommandService).deleteReview(1L, userPrincipal.getUserId())

            // Interaction
            val response: ResponseEntity<ApiResponse<Unit>> =
                controller.deleteReview(userPrincipal, 1L)

            // Assertions
            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(Unit, response.body?.data)
            verify(reviewCommandService).deleteReview(1L, userPrincipal.getUserId())
        }
    }
}
