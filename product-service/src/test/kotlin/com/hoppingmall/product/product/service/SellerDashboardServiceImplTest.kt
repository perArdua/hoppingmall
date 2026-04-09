package com.hoppingmall.product.product.service

import com.hoppingmall.product.product.domain.ProductDailyStatistics
import com.hoppingmall.product.product.domain.ProductHourlyStatistics
import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.domain.repository.ProductDailyStatisticsRepository
import com.hoppingmall.product.product.domain.repository.ProductHourlyStatisticsRepository
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.product.exception.ProductException
import com.hoppingmall.product.product.exception.ProductStatisticsNotFoundException
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
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("SellerDashboardServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class SellerDashboardServiceImplTest {

    @Mock
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @Mock
    private lateinit var productDailyStatisticsRepository: ProductDailyStatisticsRepository

    @Mock
    private lateinit var productHourlyStatisticsRepository: ProductHourlyStatisticsRepository

    @InjectMocks
    private lateinit var service: SellerDashboardServiceImpl

    private fun createStats(sellerId: Long = 1L) = ProductStatistics.create(
        productId = 1L, productName = "테스트", sellerId = sellerId, categoryId = 1L
    ).withId(1L)

    @Test
    fun 판매자_개요를_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val stats = createStats()

        whenever(productStatisticsRepository.findBySellerId(1L, pageable))
            .thenReturn(SliceImpl(listOf(stats), pageable, false))

        val result = service.getOverview(1L, pageable)

        assertThat(result.content).hasSize(1)
    }

    @Test
    fun 상품_통계를_조회한다() {
        val stats = createStats()

        whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(stats)

        val result = service.getProductStatistics(1L, 1L)

        assertThat(result.productId).isEqualTo(1L)
    }

    @Test
    fun 존재하지_않는_상품_통계_조회_시_예외를_발생시킨다() {
        whenever(productStatisticsRepository.findByProductId(999L)).thenReturn(null)

        assertThatThrownBy { service.getProductStatistics(1L, 999L) }
            .isInstanceOf(ProductStatisticsNotFoundException::class.java)
    }

    @Test
    fun 다른_판매자의_상품_통계_조회_시_예외를_발생시킨다() {
        val stats = createStats(sellerId = 2L)

        whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(stats)

        assertThatThrownBy { service.getProductStatistics(1L, 1L) }
            .isInstanceOf(ProductException::class.java)
    }

    @Test
    fun 오늘_요약을_조회한다() {
        val topSelling = createStats()

        whenever(productStatisticsRepository.countBySellerId(1L)).thenReturn(5)
        whenever(productStatisticsRepository.sumTodaySalesAmountBySellerId(1L)).thenReturn(BigDecimal("500000"))
        whenever(productStatisticsRepository.sumTodayOrderCountBySellerId(1L)).thenReturn(10)
        whenever(productStatisticsRepository.sumTodayRefundAmountBySellerId(1L)).thenReturn(BigDecimal("10000"))
        whenever(productStatisticsRepository.findTopSellingBySellerId(1L)).thenReturn(topSelling)

        val result = service.getTodaySummary(1L)

        assertThat(result.totalProducts).isEqualTo(5)
        assertThat(result.todayOrderCount).isEqualTo(10)
        assertThat(result.topSellingProductId).isEqualTo(1L)
    }

    @Test
    fun 상위_판매_상품이_없을_때_오늘_요약을_조회한다() {
        whenever(productStatisticsRepository.countBySellerId(1L)).thenReturn(0)
        whenever(productStatisticsRepository.sumTodaySalesAmountBySellerId(1L)).thenReturn(BigDecimal.ZERO)
        whenever(productStatisticsRepository.sumTodayOrderCountBySellerId(1L)).thenReturn(0)
        whenever(productStatisticsRepository.sumTodayRefundAmountBySellerId(1L)).thenReturn(BigDecimal.ZERO)
        whenever(productStatisticsRepository.findTopSellingBySellerId(1L)).thenReturn(null)

        val result = service.getTodaySummary(1L)

        assertThat(result.topSellingProductId).isNull()
        assertThat(result.topSellingProductName).isNull()
    }

    @Test
    fun 일별_통계를_조회한다() {
        val stats = createStats()
        val daily = ProductDailyStatistics.create(productId = 1L, statisticsDate = LocalDate.now()).withId(1L)

        whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(stats)
        whenever(productDailyStatisticsRepository
            .findByProductIdAndStatisticsDateBetweenOrderByStatisticsDateAsc(any(), any(), any()))
            .thenReturn(listOf(daily))

        val result = service.getDailyStatistics(1L, 1L, LocalDate.now().minusDays(7), LocalDate.now())

        assertThat(result).hasSize(1)
    }

    @Test
    fun 다른_판매자의_일별_통계_조회_시_예외를_발생시킨다() {
        val stats = createStats(sellerId = 2L)

        whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(stats)

        assertThatThrownBy { service.getDailyStatistics(1L, 1L, LocalDate.now().minusDays(7), LocalDate.now()) }
            .isInstanceOf(ProductException::class.java)
    }

    @Test
    fun 시간별_통계를_조회한다() {
        val stats = createStats()
        val hourly = ProductHourlyStatistics.create(
            productId = 1L, statisticsDate = LocalDate.now(), hour = 14
        ).withId(1L)

        whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(stats)
        whenever(productHourlyStatisticsRepository
            .findByProductIdAndStatisticsDateOrderByHourAsc(any(), any()))
            .thenReturn(listOf(hourly))

        val result = service.getHourlyStatistics(1L, 1L, LocalDate.now())

        assertThat(result).hasSize(1)
    }

    @Test
    fun 다른_판매자의_시간별_통계_조회_시_예외를_발생시킨다() {
        val stats = createStats(sellerId = 2L)

        whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(stats)

        assertThatThrownBy { service.getHourlyStatistics(1L, 1L, LocalDate.now()) }
            .isInstanceOf(ProductException::class.java)
    }
}
