package com.hoppingmall.product.product.controller

import com.hoppingmall.product.product.dto.response.*
import com.hoppingmall.product.product.service.ProductStatisticsQueryService
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

@DisplayName("ProductStatisticsController")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductStatisticsControllerTest {

    @Mock
    private lateinit var productStatisticsQueryService: ProductStatisticsQueryService

    @InjectMocks
    private lateinit var controller: ProductStatisticsController

    private fun statsResponse() = ProductStatisticsResponse(
        productId = 1L, productName = "테스트", sellerId = 1L, categoryId = 1L,
        totalSalesQuantity = 100, totalSalesAmount = BigDecimal("1000000"),
        totalRefundQuantity = 5, totalRefundAmount = BigDecimal("50000"),
        currentCartCount = 10, currentStock = 50, refundRate = BigDecimal("0.05"),
        stockTurnoverRate = BigDecimal("2.0"), todaySalesQuantity = 10,
        todaySalesAmount = BigDecimal("100000"), todayOrderCount = 5,
        todayRefundQuantity = 1, todayRefundAmount = BigDecimal("10000"),
        last7DaysSalesAmount = BigDecimal("500000"), last30DaysSalesAmount = BigDecimal("2000000"),
        salesGrowthRate = BigDecimal("10.0"), orderCount = 50,
        netRevenue = BigDecimal("950000"), averageOrderAmount = BigDecimal("20000"),
        lastAggregatedAt = java.time.LocalDateTime.now()
    )

    @Test
    fun 전체_통계를_조회한다() {
        val pageable = PageRequest.of(0, 10)

        whenever(productStatisticsQueryService.getAll(pageable))
            .thenReturn(PageImpl(listOf(statsResponse()), pageable, 1))

        val result = controller.getAll(pageable)

        assertThat(result.data).isNotNull
    }

    @Test
    fun 상품ID로_통계를_조회한다() {
        whenever(productStatisticsQueryService.getByProductId(1L)).thenReturn(statsResponse())

        val result = controller.getByProductId(1L)

        assertThat(result.data!!.productId).isEqualTo(1L)
    }

    @Test
    fun 판매자ID로_통계를_조회한다() {
        val pageable = PageRequest.of(0, 10)

        whenever(productStatisticsQueryService.getBySellerId(1L, pageable))
            .thenReturn(SliceImpl(listOf(statsResponse()), pageable, false))

        val result = controller.getBySellerId(1L, pageable)

        assertThat(result.data).isNotNull
    }

    @Test
    fun 카테고리ID로_통계를_조회한다() {
        val pageable = PageRequest.of(0, 10)

        whenever(productStatisticsQueryService.getByCategoryId(1L, pageable))
            .thenReturn(SliceImpl(listOf(statsResponse()), pageable, false))

        val result = controller.getByCategoryId(1L, pageable)

        assertThat(result.data).isNotNull
    }

    @Test
    fun 요약을_조회한다() {
        val summary = ProductStatisticsSummaryResponse(
            totalProductCount = 100, totalSalesAmount = BigDecimal("10000000"),
            totalRefundAmount = BigDecimal("500000"), averageRefundRate = BigDecimal("0.05")
        )

        whenever(productStatisticsQueryService.getSummary()).thenReturn(summary)

        val result = controller.getSummary()

        assertThat(result.data!!.totalProductCount).isEqualTo(100)
    }

    @Test
    fun 오늘_요약을_조회한다() {
        val today = TodaySummaryResponse(
            todaySalesAmount = BigDecimal("100000"), todayOrderCount = 10,
            todayRefundAmount = BigDecimal("5000")
        )

        whenever(productStatisticsQueryService.getTodaySummary()).thenReturn(today)

        val result = controller.getTodaySummary()

        assertThat(result.data!!.todayOrderCount).isEqualTo(10)
    }

    @Test
    fun 일별_통계를_조회한다() {
        val daily = ProductDailyStatisticsResponse(
            productId = 1L, statisticsDate = LocalDate.now(),
            dailySalesQuantity = 10, dailySalesAmount = BigDecimal("100000"),
            dailyOrderCount = 5, dailyRefundQuantity = 0,
            dailyRefundAmount = BigDecimal.ZERO, endOfDayStock = 90
        )

        whenever(productStatisticsQueryService.getDailyStatistics(eq(1L), any(), any()))
            .thenReturn(listOf(daily))

        val result = controller.getDailyStatistics(1L, LocalDate.now().minusDays(7), LocalDate.now())

        assertThat(result.data).hasSize(1)
    }

    @Test
    fun 일별_통계_조회_시_90일_초과하면_예외가_발생한다() {
        assertThatThrownBy {
            controller.getDailyStatistics(1L, LocalDate.now().minusDays(91), LocalDate.now())
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun 일별_통계_조회_시_시작일이_종료일보다_이후면_예외가_발생한다() {
        assertThatThrownBy {
            controller.getDailyStatistics(1L, LocalDate.now(), LocalDate.now().minusDays(1))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun 상위_판매_상품을_조회한다() {
        val top = TopProductResponse(productId = 1L, totalAmount = BigDecimal("1000000"), totalQuantity = 100)

        whenever(productStatisticsQueryService.getTopSellingProducts(7, 10)).thenReturn(listOf(top))

        val result = controller.getTopSellingProducts(7, 10)

        assertThat(result.data).hasSize(1)
    }

    @Test
    fun 상위_환불_상품을_조회한다() {
        val top = TopProductResponse(productId = 1L, totalAmount = BigDecimal("50000"), totalQuantity = 5)

        whenever(productStatisticsQueryService.getTopRefundProducts(7, 10)).thenReturn(listOf(top))

        val result = controller.getTopRefundProducts(7, 10)

        assertThat(result.data).hasSize(1)
    }

    @Test
    fun 시간별_통계를_조회한다() {
        val hourly = ProductHourlyStatisticsResponse(
            productId = 1L, statisticsDate = LocalDate.now(), hour = 14,
            hourlySalesQuantity = 10, hourlySalesAmount = BigDecimal("100000"),
            hourlyOrderCount = 5, hourlyRefundQuantity = 0,
            hourlyRefundAmount = BigDecimal.ZERO
        )

        whenever(productStatisticsQueryService.getHourlyStatistics(1L, LocalDate.now()))
            .thenReturn(listOf(hourly))

        val result = controller.getHourlyStatistics(1L, LocalDate.now())

        assertThat(result.data).hasSize(1)
    }

    @Test
    fun 피크_시간을_조회한다() {
        val peak = PeakHourResponse(hour = 14, totalSalesAmount = BigDecimal("500000"), totalOrderCount = 50)

        whenever(productStatisticsQueryService.getPeakHours(7)).thenReturn(listOf(peak))

        val result = controller.getPeakHours(7)

        assertThat(result.data).hasSize(1)
    }
}
