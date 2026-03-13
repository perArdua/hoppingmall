package com.hoppingmall.mall.product.controller

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.product.dto.response.*
import com.hoppingmall.mall.product.service.SellerDashboardService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@DisplayName("SellerDashboardController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerDashboardControllerTest {

    private val sellerDashboardService: SellerDashboardService = mock()
    private val controller = SellerDashboardController(sellerDashboardService)
    private val sellerPrincipal = UserPrincipal(1L, "seller@example.com", "SELLER")

    private fun createStatisticsResponse(productId: Long = 1L) = ProductStatisticsResponse(
        productId = productId,
        productName = "테스트 상품",
        sellerId = 1L,
        categoryId = 1L,
        totalSalesQuantity = 100,
        totalSalesAmount = BigDecimal("1000000"),
        totalRefundQuantity = 5,
        totalRefundAmount = BigDecimal("50000"),
        currentCartCount = 10,
        currentStock = 50,
        refundRate = BigDecimal("0.0500"),
        stockTurnoverRate = BigDecimal("2.0000"),
        todaySalesQuantity = 10,
        todaySalesAmount = BigDecimal("100000"),
        todayOrderCount = 5,
        todayRefundQuantity = 1,
        todayRefundAmount = BigDecimal("10000"),
        last7DaysSalesAmount = BigDecimal("700000"),
        last30DaysSalesAmount = BigDecimal("3000000"),
        salesGrowthRate = BigDecimal("20.0000"),
        orderCount = 50,
        netRevenue = BigDecimal("950000"),
        averageOrderAmount = BigDecimal("20000.00"),
        lastAggregatedAt = LocalDateTime.of(2026, 3, 13, 12, 0, 0)
    )

    @Nested
    @DisplayName("getOverview")
    inner class GetOverview {
        @Test
        fun 판매자_전체_통계를_조회한다() {
            val pageable = PageRequest.of(0, 10)
            val responses = listOf(createStatisticsResponse(1L), createStatisticsResponse(2L))
            val page = PageImpl(responses, pageable, responses.size.toLong())

            whenever(sellerDashboardService.getOverview(eq(1L), any())).thenReturn(page)

            val result: ApiResponse<Page<ProductStatisticsResponse>> = controller.getOverview(sellerPrincipal, pageable)

            assertEquals("SUCCESS", result.code)
            assertEquals(2, result.data!!.content.size)
        }
    }

    @Nested
    @DisplayName("getProductStatistics")
    inner class GetProductStatistics {
        @Test
        fun 개별_상품_통계를_조회한다() {
            val response = createStatisticsResponse(1L)

            whenever(sellerDashboardService.getProductStatistics(1L, 1L)).thenReturn(response)

            val result: ApiResponse<ProductStatisticsResponse> = controller.getProductStatistics(sellerPrincipal, 1L)

            assertEquals("SUCCESS", result.code)
            assertEquals(1L, result.data!!.productId)
        }
    }

    @Nested
    @DisplayName("getTodaySummary")
    inner class GetTodaySummary {
        @Test
        fun 오늘_매출_요약을_조회한다() {
            val summary = SellerTodaySummaryResponse(
                totalProducts = 3,
                todaySalesAmount = BigDecimal("500000"),
                todayOrderCount = 25,
                todayRefundAmount = BigDecimal("10000"),
                topSellingProductId = 1L,
                topSellingProductName = "인기상품"
            )

            whenever(sellerDashboardService.getTodaySummary(1L)).thenReturn(summary)

            val result: ApiResponse<SellerTodaySummaryResponse> = controller.getTodaySummary(sellerPrincipal)

            assertEquals("SUCCESS", result.code)
            assertEquals(3L, result.data!!.totalProducts)
            assertEquals(BigDecimal("500000"), result.data!!.todaySalesAmount)
        }
    }

    @Nested
    @DisplayName("getDailyStatistics")
    inner class GetDailyStatistics {
        @Test
        fun 일별_통계를_조회한다() {
            val startDate = LocalDate.of(2026, 3, 6)
            val endDate = LocalDate.of(2026, 3, 13)
            val dailyList = listOf(
                ProductDailyStatisticsResponse(
                    productId = 1L, statisticsDate = startDate,
                    dailySalesQuantity = 50, dailySalesAmount = BigDecimal("500000"),
                    dailyOrderCount = 10, dailyRefundQuantity = 2,
                    dailyRefundAmount = BigDecimal("20000"), endOfDayStock = 100
                )
            )

            whenever(sellerDashboardService.getDailyStatistics(1L, 1L, startDate, endDate))
                .thenReturn(dailyList)

            val result: ApiResponse<List<ProductDailyStatisticsResponse>> =
                controller.getDailyStatistics(sellerPrincipal, 1L, startDate, endDate)

            assertEquals("SUCCESS", result.code)
            assertEquals(1, result.data!!.size)
        }
    }

    @Nested
    @DisplayName("getHourlyStatistics")
    inner class GetHourlyStatistics {
        @Test
        fun 시간별_통계를_조회한다() {
            val date = LocalDate.of(2026, 3, 13)
            val hourlyList = listOf(
                ProductHourlyStatisticsResponse(
                    productId = 1L, statisticsDate = date, hour = 14,
                    hourlySalesQuantity = 30, hourlySalesAmount = BigDecimal("300000"),
                    hourlyOrderCount = 8, hourlyRefundQuantity = 1,
                    hourlyRefundAmount = BigDecimal("10000")
                )
            )

            whenever(sellerDashboardService.getHourlyStatistics(1L, 1L, date))
                .thenReturn(hourlyList)

            val result: ApiResponse<List<ProductHourlyStatisticsResponse>> =
                controller.getHourlyStatistics(sellerPrincipal, 1L, date)

            assertEquals("SUCCESS", result.code)
            assertEquals(1, result.data!!.size)
            assertEquals(14, result.data!![0].hour)
        }
    }
}
