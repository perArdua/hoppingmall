package com.hoppingmall.product.review.service

import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.review.domain.repository.ReviewRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.cache.CacheManager
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal

@DisplayName("ProductReviewSummaryService")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductReviewSummaryServiceTest {

    @Mock
    private lateinit var productRepository: ProductRepository

    @Mock
    private lateinit var reviewRepository: ReviewRepository

    @Mock
    private lateinit var cacheManagerProvider: ObjectProvider<CacheManager>

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    private fun service(batchSize: Int = 2) =
        ProductReviewSummaryService(productRepository, reviewRepository, cacheManagerProvider, transactionManager, batchSize)

    @Test
    fun 상품_행이_없으면_집계를_쓰지_않는다() {
        whenever(productRepository.lockIdForUpdate(9L)).thenReturn(null)

        service().refresh(9L)

        verify(productRepository, never()).updateReviewSummary(any(), any(), any())
    }

    @Test
    fun 리뷰가_없으면_리뷰_수_0과_평점_0으로_쓴다() {
        whenever(productRepository.lockIdForUpdate(9L)).thenReturn(9L)
        whenever(reviewRepository.countByProductId(9L)).thenReturn(0L)
        whenever(reviewRepository.averageRatingByProductId(9L)).thenReturn(null)

        service().refresh(9L)

        verify(productRepository).updateReviewSummary(9L, 0L, BigDecimal("0.00"))
    }

    @Test
    fun 채우기_중_한_상품이_실패해도_나머지를_채우고_실패_수를_센다() {
        whenever(productRepository.findIdsAfter(0L, 2)).thenReturn(listOf(1L, 2L))
        whenever(productRepository.findIdsAfter(2L, 2)).thenReturn(listOf(3L))
        whenever(productRepository.lockIdForUpdate(eq(1L))).thenReturn(1L)
        whenever(productRepository.lockIdForUpdate(eq(2L))).thenThrow(IllegalStateException("잠금 실패"))
        whenever(productRepository.lockIdForUpdate(eq(3L))).thenReturn(3L)
        whenever(reviewRepository.countByProductId(any())).thenReturn(1L)
        whenever(reviewRepository.averageRatingByProductId(any())).thenReturn(4.0)

        val result = service().backfill()

        assertThat(result).isEqualTo(ProductReviewSummaryService.BackfillResult(refreshed = 2, failed = 1))
        verify(productRepository).updateReviewSummary(1L, 1L, BigDecimal("4.00"))
        verify(productRepository).updateReviewSummary(3L, 1L, BigDecimal("4.00"))
    }

    @Test
    fun 채우기_배치_크기가_0이면_기동하지_않는다() {
        assertThatThrownBy { service(batchSize = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
