package com.hoppingmall.product.internal

import com.hoppingmall.product.review.service.ProductReviewSummaryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

@DisplayName("InternalProductReviewSummaryController")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class InternalProductReviewSummaryControllerTest {

    @Mock
    private lateinit var productReviewSummaryService: ProductReviewSummaryService

    @InjectMocks
    private lateinit var controller: InternalProductReviewSummaryController

    @Test
    fun 리뷰_집계_채우기_결과를_돌려준다() {
        whenever(productReviewSummaryService.backfill()).thenReturn(ProductReviewSummaryService.BackfillResult(3, 1))

        val result = controller.backfill()

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body).isEqualTo(ProductReviewSummaryService.BackfillResult(3, 1))
    }
}
