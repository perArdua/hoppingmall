package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.domain.ProductStatistics
import com.hoppingmall.mall.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.mall.product.exception.ProductStatisticsNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal

@DisplayName("ProductStatisticsQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ProductStatisticsQueryServiceImplTest {

    private val productStatisticsRepository: ProductStatisticsRepository = mock()
    private val service = ProductStatisticsQueryServiceImpl(productStatisticsRepository)

    @Nested
    @DisplayName("getAll")
    inner class GetAll {
        @Test
        fun 전체_통계_조회_성공() {
            val pageable = PageRequest.of(0, 10)
            val stats = listOf(
                ProductStatistics.fixture(productId = 1L, productName = "상품1").withId(1L),
                ProductStatistics.fixture(productId = 2L, productName = "상품2", sellerId = 2L).withId(2L)
            )
            val page = PageImpl(stats, pageable, stats.size.toLong())

            whenever(productStatisticsRepository.findAll(pageable)).thenReturn(page)

            val result = service.getAll(pageable)

            assertEquals(2, result.content.size)
            assertEquals("상품1", result.content[0].productName)
            assertEquals("상품2", result.content[1].productName)
            verify(productStatisticsRepository).findAll(pageable)
        }
    }

    @Nested
    @DisplayName("getByProductId")
    inner class GetByProductId {
        @Test
        fun 상품별_통계_조회_성공() {
            val productId = 1L
            val stats = ProductStatistics.fixture(productId = productId, productName = "테스트 상품").withId(1L)

            whenever(productStatisticsRepository.findByProductId(productId)).thenReturn(stats)

            val result = service.getByProductId(productId)

            assertEquals(productId, result.productId)
            assertEquals("테스트 상품", result.productName)
            assertEquals(100, result.totalSalesQuantity)
            verify(productStatisticsRepository).findByProductId(productId)
        }

        @Test
        fun 존재하지_않는_상품_통계_조회_시_예외_발생() {
            val productId = 999L

            whenever(productStatisticsRepository.findByProductId(productId)).thenReturn(null)

            assertThrows(ProductStatisticsNotFoundException::class.java) {
                service.getByProductId(productId)
            }

            verify(productStatisticsRepository).findByProductId(productId)
        }
    }

    @Nested
    @DisplayName("getBySellerId")
    inner class GetBySellerId {
        @Test
        fun 판매자별_통계_조회_성공() {
            val sellerId = 1L
            val pageable = PageRequest.of(0, 10)
            val stats = listOf(
                ProductStatistics.fixture(productId = 1L, sellerId = sellerId, productName = "상품1").withId(1L),
                ProductStatistics.fixture(productId = 2L, sellerId = sellerId, productName = "상품2").withId(2L)
            )
            val page = PageImpl(stats, pageable, stats.size.toLong())

            whenever(productStatisticsRepository.findBySellerId(sellerId, pageable)).thenReturn(page)

            val result = service.getBySellerId(sellerId, pageable)

            assertEquals(2, result.content.size)
            assertEquals(sellerId, result.content[0].sellerId)
            assertEquals(sellerId, result.content[1].sellerId)
            verify(productStatisticsRepository).findBySellerId(sellerId, pageable)
        }
    }

    @Nested
    @DisplayName("getByCategoryId")
    inner class GetByCategoryId {
        @Test
        fun 카테고리별_통계_조회_성공() {
            val categoryId = 1L
            val pageable = PageRequest.of(0, 10)
            val stats = listOf(
                ProductStatistics.fixture(productId = 1L, categoryId = categoryId, productName = "상품1").withId(1L)
            )
            val page = PageImpl(stats, pageable, stats.size.toLong())

            whenever(productStatisticsRepository.findByCategoryId(categoryId, pageable)).thenReturn(page)

            val result = service.getByCategoryId(categoryId, pageable)

            assertEquals(1, result.content.size)
            assertEquals(categoryId, result.content[0].categoryId)
            verify(productStatisticsRepository).findByCategoryId(categoryId, pageable)
        }
    }

    @Nested
    @DisplayName("getSummary")
    inner class GetSummary {
        @Test
        fun 전체_요약_조회_성공() {
            whenever(productStatisticsRepository.countAllProducts()).thenReturn(10L)
            whenever(productStatisticsRepository.sumTotalSalesAmount()).thenReturn(BigDecimal("5000000"))
            whenever(productStatisticsRepository.sumTotalRefundAmount()).thenReturn(BigDecimal("250000"))
            whenever(productStatisticsRepository.avgRefundRate()).thenReturn(BigDecimal("0.0500"))

            val result = service.getSummary()

            assertEquals(10L, result.totalProductCount)
            assertEquals(BigDecimal("5000000"), result.totalSalesAmount)
            assertEquals(BigDecimal("250000"), result.totalRefundAmount)
            assertEquals(BigDecimal("0.0500"), result.averageRefundRate)
            verify(productStatisticsRepository).countAllProducts()
            verify(productStatisticsRepository).sumTotalSalesAmount()
            verify(productStatisticsRepository).sumTotalRefundAmount()
            verify(productStatisticsRepository).avgRefundRate()
        }
    }
}
