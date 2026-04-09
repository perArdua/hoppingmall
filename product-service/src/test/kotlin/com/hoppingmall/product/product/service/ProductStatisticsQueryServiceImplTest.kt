package com.hoppingmall.product.product.service

import com.hoppingmall.product.product.domain.ProductDailyStatistics
import com.hoppingmall.product.product.domain.ProductHourlyStatistics
import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.domain.repository.ProductDailyStatisticsRepository
import com.hoppingmall.product.product.domain.repository.ProductHourlyStatisticsRepository
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.product.dto.HourlyAggregationProjection
import com.hoppingmall.product.product.dto.TopProductProjection
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("ProductStatisticsQueryServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductStatisticsQueryServiceImplTest {

    @Mock
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @Mock
    private lateinit var productDailyStatisticsRepository: ProductDailyStatisticsRepository

    @Mock
    private lateinit var productHourlyStatisticsRepository: ProductHourlyStatisticsRepository

    @InjectMocks
    private lateinit var service: ProductStatisticsQueryServiceImpl

    private fun createStats() = ProductStatistics.create(
        productId = 1L, productName = "테스트", sellerId = 1L, categoryId = 1L
    ).withId(1L)

    @Test
    fun 전체_통계를_페이지로_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val stats = createStats()
        val page = PageImpl(listOf(stats), pageable, 1)

        whenever(productStatisticsRepository.findAll(pageable)).thenReturn(page)

        val result = service.getAll(pageable)

        assertThat(result.content).hasSize(1)
    }

    @Test
    fun 상품ID로_통계를_조회한다() {
        val stats = createStats()

        whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(stats)

        val result = service.getByProductId(1L)

        assertThat(result.productId).isEqualTo(1L)
    }

    @Test
    fun 존재하지_않는_상품_통계_조회_시_예외를_발생시킨다() {
        whenever(productStatisticsRepository.findByProductId(999L)).thenReturn(null)

        assertThatThrownBy { service.getByProductId(999L) }
            .isInstanceOf(ProductStatisticsNotFoundException::class.java)
    }

    @Test
    fun 판매자ID로_통계를_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val stats = createStats()

        whenever(productStatisticsRepository.findBySellerId(1L, pageable))
            .thenReturn(SliceImpl(listOf(stats), pageable, false))

        val result = service.getBySellerId(1L, pageable)

        assertThat(result.content).hasSize(1)
    }

    @Test
    fun 카테고리ID로_통계를_조회한다() {
        val pageable = PageRequest.of(0, 10)
        val stats = createStats()

        whenever(productStatisticsRepository.findByCategoryId(1L, pageable))
            .thenReturn(SliceImpl(listOf(stats), pageable, false))

        val result = service.getByCategoryId(1L, pageable)

        assertThat(result.content).hasSize(1)
    }

    @Test
    fun 요약_통계를_조회한다() {
        whenever(productStatisticsRepository.countAllProducts()).thenReturn(100)
        whenever(productStatisticsRepository.sumTotalSalesAmount()).thenReturn(BigDecimal("10000000"))
        whenever(productStatisticsRepository.sumTotalRefundAmount()).thenReturn(BigDecimal("500000"))
        whenever(productStatisticsRepository.avgRefundRate()).thenReturn(BigDecimal("0.05"))

        val result = service.getSummary()

        assertThat(result.totalProductCount).isEqualTo(100)
    }

    @Test
    fun 오늘_요약을_조회한다() {
        whenever(productStatisticsRepository.sumTodaySalesAmount()).thenReturn(BigDecimal("100000"))
        whenever(productStatisticsRepository.sumTodayOrderCount()).thenReturn(10)
        whenever(productStatisticsRepository.sumTodayRefundAmount()).thenReturn(BigDecimal("5000"))

        val result = service.getTodaySummary()

        assertThat(result.todayOrderCount).isEqualTo(10)
    }

    @Test
    fun 일별_통계를_조회한다() {
        val daily = ProductDailyStatistics.create(productId = 1L, statisticsDate = LocalDate.now()).withId(1L)

        whenever(productDailyStatisticsRepository
            .findByProductIdAndStatisticsDateBetweenOrderByStatisticsDateAsc(any(), any(), any()))
            .thenReturn(listOf(daily))

        val result = service.getDailyStatistics(1L, LocalDate.now().minusDays(7), LocalDate.now())

        assertThat(result).hasSize(1)
    }

    @Test
    fun 상위_판매_상품을_조회한다() {
        val projection = object : TopProductProjection {
            override fun getProductId() = 1L
            override fun getTotalAmount() = BigDecimal("1000000")
            override fun getTotalQuantity() = 100L
        }

        whenever(productDailyStatisticsRepository.findTopSellingProducts(any(), any(), eq(10)))
            .thenReturn(listOf(projection))

        val result = service.getTopSellingProducts(7, 10)

        assertThat(result).hasSize(1)
    }

    @Test
    fun 상위_환불_상품을_조회한다() {
        val projection = object : TopProductProjection {
            override fun getProductId() = 1L
            override fun getTotalAmount() = BigDecimal("50000")
            override fun getTotalQuantity() = 5L
        }

        whenever(productDailyStatisticsRepository.findTopRefundProducts(any(), any(), eq(10)))
            .thenReturn(listOf(projection))

        val result = service.getTopRefundProducts(7, 10)

        assertThat(result).hasSize(1)
    }

    @Test
    fun 시간별_통계를_조회한다() {
        val hourly = ProductHourlyStatistics.create(
            productId = 1L, statisticsDate = LocalDate.now(), hour = 14
        ).withId(1L)

        whenever(productHourlyStatisticsRepository
            .findByProductIdAndStatisticsDateOrderByHourAsc(1L, LocalDate.now()))
            .thenReturn(listOf(hourly))

        val result = service.getHourlyStatistics(1L, LocalDate.now())

        assertThat(result).hasSize(1)
    }

    @Test
    fun 피크_시간을_조회한다() {
        val projection = object : HourlyAggregationProjection {
            override fun getHour() = 14
            override fun getTotalAmount() = BigDecimal("500000")
            override fun getTotalOrders() = 50L
        }

        whenever(productHourlyStatisticsRepository.findPeakHours(any(), any()))
            .thenReturn(listOf(projection))

        val result = service.getPeakHours(7)

        assertThat(result).hasSize(1)
    }
}
