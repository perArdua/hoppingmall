package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.domain.ProductDailyStatistics
import com.hoppingmall.mall.product.domain.ProductStatistics
import com.hoppingmall.mall.product.domain.repository.ProductDailyStatisticsRepository
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.domain.repository.ProductStatisticsRepository
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
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@DisplayName("ProductStatisticsCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ProductStatisticsCommandServiceImplTest {

    private val productStatisticsRepository: ProductStatisticsRepository = mock()
    private val productDailyStatisticsRepository: ProductDailyStatisticsRepository = mock()
    private val productRepository: ProductRepository = mock()

    private val service = ProductStatisticsCommandServiceImpl(
        productStatisticsRepository,
        productDailyStatisticsRepository,
        productRepository
    )

    @Nested
    @DisplayName("incrementSalesStats")
    inner class IncrementSalesStats {
        @Test
        fun 기존_통계가_있으면_판매_증분한다() {
            val stats = ProductStatistics.fixture(productId = 1L).withId(1L)
            whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(stats)
            whenever(productStatisticsRepository.save(any<ProductStatistics>())).thenAnswer { it.arguments[0] }

            service.incrementSalesStats(1L, 5, BigDecimal("50000"))

            assertEquals(105, stats.totalSalesQuantity)
            assertEquals(BigDecimal("1050000"), stats.totalSalesAmount)
            verify(productStatisticsRepository).save(stats)
        }

        @Test
        fun 기존_통계가_없으면_새로_생성_후_증분한다() {
            whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(null)
            val product = com.hoppingmall.mall.product.domain.Product.fixture(sellerId = 2L, categoryId = 3L).withId(1L)
            whenever(productRepository.findById(1L)).thenReturn(Optional.of(product))
            whenever(productStatisticsRepository.save(any<ProductStatistics>())).thenAnswer { invocation ->
                (invocation.arguments[0] as ProductStatistics).withId(1L)
            }

            service.incrementSalesStats(1L, 5, BigDecimal("50000"))

            verify(productStatisticsRepository, times(2)).save(any<ProductStatistics>())
        }

        @Test
        fun 상품이_존재하지_않으면_예외_발생() {
            whenever(productStatisticsRepository.findByProductId(999L)).thenReturn(null)
            whenever(productRepository.findById(999L)).thenReturn(Optional.empty())

            assertThrows<IllegalArgumentException> {
                service.incrementSalesStats(999L, 1, BigDecimal("10000"))
            }
        }
    }

    @Nested
    @DisplayName("decrementSalesStats")
    inner class DecrementSalesStats {
        @Test
        fun 기존_통계가_있으면_판매_차감한다() {
            val stats = ProductStatistics.fixture(productId = 1L, totalSalesQuantity = 100, totalSalesAmount = BigDecimal("1000000")).withId(1L)
            whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(stats)
            whenever(productStatisticsRepository.save(any<ProductStatistics>())).thenAnswer { it.arguments[0] }

            service.decrementSalesStats(1L, 3, BigDecimal("30000"))

            assertEquals(97, stats.totalSalesQuantity)
            assertEquals(BigDecimal("970000"), stats.totalSalesAmount)
            verify(productStatisticsRepository).save(stats)
        }

        @Test
        fun 기존_통계가_없으면_아무_작업도_하지_않는다() {
            whenever(productStatisticsRepository.findByProductId(999L)).thenReturn(null)

            service.decrementSalesStats(999L, 3, BigDecimal("30000"))

            verify(productStatisticsRepository, never()).save(any<ProductStatistics>())
        }
    }

    @Nested
    @DisplayName("incrementRefundStats")
    inner class IncrementRefundStats {
        @Test
        fun 환불_통계가_증가한다() {
            val stats = ProductStatistics.fixture(productId = 1L).withId(1L)
            whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(stats)
            whenever(productStatisticsRepository.save(any<ProductStatistics>())).thenAnswer { it.arguments[0] }

            service.incrementRefundStats(1L, 2, BigDecimal("20000"))

            assertEquals(7, stats.totalRefundQuantity)
            assertEquals(BigDecimal("70000"), stats.totalRefundAmount)
            verify(productStatisticsRepository).save(stats)
        }
    }

    @Nested
    @DisplayName("flushDailySnapshot")
    inner class FlushDailySnapshot {
        @Test
        fun 일별_스냅샷을_저장하고_오늘_지표를_리셋한다() {
            val stats = ProductStatistics.fixture(productId = 1L).withId(1L)
            stats.incrementSales(5, BigDecimal("50000"))

            whenever(productStatisticsRepository.findAll()).thenReturn(listOf(stats))
            whenever(productDailyStatisticsRepository.findByProductIdAndStatisticsDate(eq(1L), any()))
                .thenReturn(null)
            whenever(productDailyStatisticsRepository.save(any<ProductDailyStatistics>()))
                .thenAnswer { it.arguments[0] }
            whenever(productDailyStatisticsRepository.sumSalesAmountByProductIdAndDateRange(eq(1L), any(), any()))
                .thenReturn(BigDecimal("300000"))
            whenever(productStatisticsRepository.save(any<ProductStatistics>())).thenAnswer { it.arguments[0] }

            service.flushDailySnapshot()

            assertEquals(0, stats.todaySalesQuantity)
            assertEquals(BigDecimal.ZERO, stats.todaySalesAmount)
            verify(productDailyStatisticsRepository).save(any<ProductDailyStatistics>())
            verify(productStatisticsRepository).save(stats)
        }

        @Test
        fun 기존_일별_통계가_있으면_업데이트한다() {
            val stats = ProductStatistics.fixture(productId = 1L).withId(1L)
            stats.incrementSales(3, BigDecimal("30000"))

            val existingDaily = ProductDailyStatistics.create(
                productId = 1L,
                statisticsDate = LocalDate.now()
            ).withId(1L)

            whenever(productStatisticsRepository.findAll()).thenReturn(listOf(stats))
            whenever(productDailyStatisticsRepository.findByProductIdAndStatisticsDate(eq(1L), any()))
                .thenReturn(existingDaily)
            whenever(productDailyStatisticsRepository.save(any<ProductDailyStatistics>()))
                .thenAnswer { it.arguments[0] }
            whenever(productDailyStatisticsRepository.sumSalesAmountByProductIdAndDateRange(eq(1L), any(), any()))
                .thenReturn(BigDecimal("200000"))
            whenever(productStatisticsRepository.save(any<ProductStatistics>())).thenAnswer { it.arguments[0] }

            service.flushDailySnapshot()

            assertEquals(3, existingDaily.dailySalesQuantity)
            verify(productDailyStatisticsRepository).save(existingDaily)
        }
    }
}
