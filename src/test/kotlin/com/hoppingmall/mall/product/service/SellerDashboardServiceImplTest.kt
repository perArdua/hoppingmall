package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.domain.ProductHourlyStatistics
import com.hoppingmall.mall.product.domain.ProductStatistics
import com.hoppingmall.mall.product.domain.repository.ProductDailyStatisticsRepository
import com.hoppingmall.mall.product.domain.repository.ProductHourlyStatisticsRepository
import com.hoppingmall.mall.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.mall.product.exception.ProductException
import com.hoppingmall.mall.product.exception.ProductStatisticsNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("SellerDashboardServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerDashboardServiceImplTest {

    private val productStatisticsRepository: ProductStatisticsRepository = mock()
    private val productDailyStatisticsRepository: ProductDailyStatisticsRepository = mock()
    private val productHourlyStatisticsRepository: ProductHourlyStatisticsRepository = mock()

    private val service = SellerDashboardServiceImpl(
        productStatisticsRepository,
        productDailyStatisticsRepository,
        productHourlyStatisticsRepository
    )

    @Nested
    @DisplayName("getOverview")
    inner class GetOverview {
        @Test
        fun 판매자_전체_상품_통계를_조회한다() {
            val sellerId = 1L
            val pageable = PageRequest.of(0, 10)
            val stats = listOf(
                ProductStatistics.fixture(productId = 1L, sellerId = sellerId, productName = "상품1").withId(1L),
                ProductStatistics.fixture(productId = 2L, sellerId = sellerId, productName = "상품2").withId(2L)
            )
            val page = PageImpl(stats, pageable, stats.size.toLong())

            whenever(productStatisticsRepository.findBySellerId(sellerId, pageable)).thenReturn(page)

            val result = service.getOverview(sellerId, pageable)

            assertEquals(2, result.content.size)
            verify(productStatisticsRepository).findBySellerId(sellerId, pageable)
        }
    }

    @Nested
    @DisplayName("getProductStatistics")
    inner class GetProductStatistics {
        @Test
        fun 본인_상품_통계를_조회한다() {
            val sellerId = 1L
            val productId = 1L
            val stats = ProductStatistics.fixture(productId = productId, sellerId = sellerId).withId(1L)

            whenever(productStatisticsRepository.findByProductId(productId)).thenReturn(stats)

            val result = service.getProductStatistics(sellerId, productId)

            assertEquals(productId, result.productId)
            assertEquals(sellerId, result.sellerId)
        }

        @Test
        fun 존재하지_않는_상품이면_예외가_발생한다() {
            whenever(productStatisticsRepository.findByProductId(999L)).thenReturn(null)

            assertThrows<ProductStatisticsNotFoundException> {
                service.getProductStatistics(1L, 999L)
            }
        }

        @Test
        fun 타인의_상품이면_접근_거부_예외가_발생한다() {
            val stats = ProductStatistics.fixture(productId = 1L, sellerId = 2L).withId(1L)

            whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(stats)

            assertThrows<ProductException> {
                service.getProductStatistics(1L, 1L)
            }
        }
    }

    @Nested
    @DisplayName("getTodaySummary")
    inner class GetTodaySummary {
        @Test
        fun 판매자_오늘_매출_요약을_조회한다() {
            val sellerId = 1L
            val topStats = ProductStatistics.fixture(productId = 1L, sellerId = sellerId, productName = "인기상품").withId(1L)

            whenever(productStatisticsRepository.countBySellerId(sellerId)).thenReturn(3L)
            whenever(productStatisticsRepository.sumTodaySalesAmountBySellerId(sellerId)).thenReturn(BigDecimal("500000"))
            whenever(productStatisticsRepository.sumTodayOrderCountBySellerId(sellerId)).thenReturn(25L)
            whenever(productStatisticsRepository.sumTodayRefundAmountBySellerId(sellerId)).thenReturn(BigDecimal("10000"))
            whenever(productStatisticsRepository.findTopSellingBySellerId(sellerId)).thenReturn(topStats)

            val result = service.getTodaySummary(sellerId)

            assertEquals(3L, result.totalProducts)
            assertEquals(BigDecimal("500000"), result.todaySalesAmount)
            assertEquals(25L, result.todayOrderCount)
            assertEquals(BigDecimal("10000"), result.todayRefundAmount)
            assertEquals(1L, result.topSellingProductId)
            assertEquals("인기상품", result.topSellingProductName)
        }

        @Test
        fun 판매_상품이_없으면_null_값을_반환한다() {
            val sellerId = 1L

            whenever(productStatisticsRepository.countBySellerId(sellerId)).thenReturn(0L)
            whenever(productStatisticsRepository.sumTodaySalesAmountBySellerId(sellerId)).thenReturn(BigDecimal.ZERO)
            whenever(productStatisticsRepository.sumTodayOrderCountBySellerId(sellerId)).thenReturn(0L)
            whenever(productStatisticsRepository.sumTodayRefundAmountBySellerId(sellerId)).thenReturn(BigDecimal.ZERO)
            whenever(productStatisticsRepository.findTopSellingBySellerId(sellerId)).thenReturn(null)

            val result = service.getTodaySummary(sellerId)

            assertEquals(0L, result.totalProducts)
            assertEquals(null, result.topSellingProductId)
            assertEquals(null, result.topSellingProductName)
        }
    }

    @Nested
    @DisplayName("getDailyStatistics")
    inner class GetDailyStatistics {
        @Test
        fun 본인_상품의_일별_통계를_조회한다() {
            val sellerId = 1L
            val productId = 1L
            val stats = ProductStatistics.fixture(productId = productId, sellerId = sellerId).withId(1L)

            whenever(productStatisticsRepository.findByProductId(productId)).thenReturn(stats)
            whenever(productDailyStatisticsRepository.findByProductIdAndStatisticsDateBetweenOrderByStatisticsDateAsc(
                eq(productId), any(), any()
            )).thenReturn(emptyList())

            val result = service.getDailyStatistics(sellerId, productId, LocalDate.now().minusDays(7), LocalDate.now())

            assertEquals(0, result.size)
            verify(productStatisticsRepository).findByProductId(productId)
        }

        @Test
        fun 타인_상품의_일별_통계_조회_시_예외가_발생한다() {
            val stats = ProductStatistics.fixture(productId = 1L, sellerId = 2L).withId(1L)

            whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(stats)

            assertThrows<ProductException> {
                service.getDailyStatistics(1L, 1L, LocalDate.now().minusDays(7), LocalDate.now())
            }
        }
    }

    @Nested
    @DisplayName("getHourlyStatistics")
    inner class GetHourlyStatistics {
        @Test
        fun 본인_상품의_시간별_통계를_조회한다() {
            val sellerId = 1L
            val productId = 1L
            val date = LocalDate.of(2026, 3, 13)
            val stats = ProductStatistics.fixture(productId = productId, sellerId = sellerId).withId(1L)
            val hourlyList = listOf(
                ProductHourlyStatistics.fixture(productId = productId, statisticsDate = date, hour = 10).withId(1L)
            )

            whenever(productStatisticsRepository.findByProductId(productId)).thenReturn(stats)
            whenever(productHourlyStatisticsRepository.findByProductIdAndStatisticsDateOrderByHourAsc(productId, date))
                .thenReturn(hourlyList)

            val result = service.getHourlyStatistics(sellerId, productId, date)

            assertEquals(1, result.size)
            assertEquals(10, result[0].hour)
        }

        @Test
        fun 타인_상품의_시간별_통계_조회_시_예외가_발생한다() {
            val stats = ProductStatistics.fixture(productId = 1L, sellerId = 2L).withId(1L)

            whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(stats)

            assertThrows<ProductException> {
                service.getHourlyStatistics(1L, 1L, LocalDate.now())
            }
        }
    }
}
