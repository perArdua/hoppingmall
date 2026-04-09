package com.hoppingmall.product.product.controller

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.product.product.dto.response.*
import com.hoppingmall.product.product.service.SellerDashboardService
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
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("SellerDashboardController")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class SellerDashboardControllerTest {

    @Mock
    private lateinit var sellerDashboardService: SellerDashboardService

    @InjectMocks
    private lateinit var controller: SellerDashboardController

    private val principal = UserPrincipal(1L, "SELLER")

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
    fun 판매자_개요를_조회한다() {
        val pageable = PageRequest.of(0, 10)

        whenever(sellerDashboardService.getOverview(eq(1L), eq(pageable)))
            .thenReturn(SliceImpl(listOf(statsResponse()), pageable, false))

        val result = controller.getOverview(principal, pageable)

        assertThat(result.data).isNotNull
    }

    @Test
    fun 상품_통계를_조회한다() {
        whenever(sellerDashboardService.getProductStatistics(1L, 1L)).thenReturn(statsResponse())

        val result = controller.getProductStatistics(principal, 1L)

        assertThat(result.data!!.productId).isEqualTo(1L)
    }

    @Test
    fun 오늘_요약을_조회한다() {
        val summary = SellerTodaySummaryResponse(
            totalProducts = 5, todaySalesAmount = BigDecimal("500000"),
            todayOrderCount = 10, todayRefundAmount = BigDecimal("10000"),
            topSellingProductId = 1L, topSellingProductName = "테스트"
        )

        whenever(sellerDashboardService.getTodaySummary(1L)).thenReturn(summary)

        val result = controller.getTodaySummary(principal)

        assertThat(result.data!!.totalProducts).isEqualTo(5)
    }

    @Test
    fun 일별_통계를_조회한다() {
        val daily = ProductDailyStatisticsResponse(
            productId = 1L, statisticsDate = LocalDate.now(),
            dailySalesQuantity = 10, dailySalesAmount = BigDecimal("100000"),
            dailyOrderCount = 5, dailyRefundQuantity = 0,
            dailyRefundAmount = BigDecimal.ZERO, endOfDayStock = 90
        )

        whenever(sellerDashboardService.getDailyStatistics(eq(1L), eq(1L), any(), any()))
            .thenReturn(listOf(daily))

        val result = controller.getDailyStatistics(principal, 1L, LocalDate.now().minusDays(7), LocalDate.now())

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

        whenever(sellerDashboardService.getHourlyStatistics(eq(1L), eq(1L), any()))
            .thenReturn(listOf(hourly))

        val result = controller.getHourlyStatistics(principal, 1L, LocalDate.now())

        assertThat(result.data).hasSize(1)
    }
}
