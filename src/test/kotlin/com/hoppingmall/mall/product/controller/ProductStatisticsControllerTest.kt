package com.hoppingmall.mall.product.controller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.product.dto.response.*
import com.hoppingmall.mall.product.exception.ProductStatisticsNotFoundException
import com.hoppingmall.mall.product.service.ProductStatisticsQueryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@DisplayName("ProductStatisticsController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ProductStatisticsControllerTest {

    private val productStatisticsQueryService: ProductStatisticsQueryService = mock()
    private val controller = ProductStatisticsController(productStatisticsQueryService)

    private fun createResponse(
        productId: Long = 1L,
        productName: String = "테스트 상품"
    ) = ProductStatisticsResponse(
        productId = productId,
        productName = productName,
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
        lastAggregatedAt = LocalDateTime.of(2026, 2, 17, 12, 0, 0)
    )

    @Nested
    @DisplayName("getAll")
    inner class GetAll {
        @Test
        fun 전체_통계_조회_성공() {
            val pageable = PageRequest.of(0, 10)
            val responses = listOf(createResponse(1L, "상품1"), createResponse(2L, "상품2"))
            val page = PageImpl(responses, pageable, responses.size.toLong())

            whenever(productStatisticsQueryService.getAll(any())).thenReturn(page)

            val result: ApiResponse<Page<ProductStatisticsResponse>> = controller.getAll(pageable)

            assertEquals("SUCCESS", result.code)
            assertEquals(2, result.data!!.content.size)
            verify(productStatisticsQueryService).getAll(pageable)
        }
    }

    @Nested
    @DisplayName("getByProductId")
    inner class GetByProductId {
        @Test
        fun 상품별_통계_조회_성공() {
            val productId = 1L
            val response = createResponse(productId)

            whenever(productStatisticsQueryService.getByProductId(productId)).thenReturn(response)

            val result: ApiResponse<ProductStatisticsResponse> = controller.getByProductId(productId)

            assertEquals("SUCCESS", result.code)
            assertEquals(productId, result.data!!.productId)
            verify(productStatisticsQueryService).getByProductId(productId)
        }

        @Test
        fun 존재하지_않는_상품_통계_조회_시_예외_발생() {
            val productId = 999L

            whenever(productStatisticsQueryService.getByProductId(productId))
                .thenThrow(ProductStatisticsNotFoundException())

            assertThrows<ProductStatisticsNotFoundException> {
                controller.getByProductId(productId)
            }

            verify(productStatisticsQueryService).getByProductId(productId)
        }
    }

    @Nested
    @DisplayName("getBySellerId")
    inner class GetBySellerId {
        @Test
        fun 판매자별_통계_조회_성공() {
            val sellerId = 1L
            val pageable = PageRequest.of(0, 10)
            val responses = listOf(createResponse(1L, "상품1"))
            val page = PageImpl(responses, pageable, responses.size.toLong())

            whenever(productStatisticsQueryService.getBySellerId(eq(sellerId), any())).thenReturn(page)

            val result: ApiResponse<Page<ProductStatisticsResponse>> = controller.getBySellerId(sellerId, pageable)

            assertEquals("SUCCESS", result.code)
            assertEquals(1, result.data!!.content.size)
            verify(productStatisticsQueryService).getBySellerId(sellerId, pageable)
        }
    }

    @Nested
    @DisplayName("getByCategoryId")
    inner class GetByCategoryId {
        @Test
        fun 카테고리별_통계_조회_성공() {
            val categoryId = 1L
            val pageable = PageRequest.of(0, 10)
            val responses = listOf(createResponse(1L, "상품1"))
            val page = PageImpl(responses, pageable, responses.size.toLong())

            whenever(productStatisticsQueryService.getByCategoryId(eq(categoryId), any())).thenReturn(page)

            val result: ApiResponse<Page<ProductStatisticsResponse>> = controller.getByCategoryId(categoryId, pageable)

            assertEquals("SUCCESS", result.code)
            assertEquals(1, result.data!!.content.size)
            verify(productStatisticsQueryService).getByCategoryId(categoryId, pageable)
        }
    }

    @Nested
    @DisplayName("getSummary")
    inner class GetSummary {
        @Test
        fun 전체_요약_조회_성공() {
            val summary = ProductStatisticsSummaryResponse(
                totalProductCount = 10,
                totalSalesAmount = BigDecimal("5000000"),
                totalRefundAmount = BigDecimal("250000"),
                averageRefundRate = BigDecimal("0.0500")
            )

            whenever(productStatisticsQueryService.getSummary()).thenReturn(summary)

            val result: ApiResponse<ProductStatisticsSummaryResponse> = controller.getSummary()

            assertEquals("SUCCESS", result.code)
            assertEquals(10L, result.data!!.totalProductCount)
            assertEquals(BigDecimal("5000000"), result.data!!.totalSalesAmount)
            verify(productStatisticsQueryService).getSummary()
        }
    }

    @Nested
    @DisplayName("getTodaySummary")
    inner class GetTodaySummary {
        @Test
        fun 오늘_실시간_요약_조회_성공() {
            val todaySummary = TodaySummaryResponse(
                todaySalesAmount = BigDecimal("1000000"),
                todayOrderCount = 50,
                todayRefundAmount = BigDecimal("50000")
            )

            whenever(productStatisticsQueryService.getTodaySummary()).thenReturn(todaySummary)

            val result: ApiResponse<TodaySummaryResponse> = controller.getTodaySummary()

            assertEquals("SUCCESS", result.code)
            assertEquals(BigDecimal("1000000"), result.data!!.todaySalesAmount)
            assertEquals(50L, result.data!!.todayOrderCount)
            verify(productStatisticsQueryService).getTodaySummary()
        }
    }

    @Nested
    @DisplayName("getDailyStatistics")
    inner class GetDailyStatistics {
        @Test
        fun 일별_통계_조회_성공() {
            val productId = 1L
            val startDate = LocalDate.of(2026, 2, 10)
            val endDate = LocalDate.of(2026, 2, 18)
            val dailyList = listOf(
                ProductDailyStatisticsResponse(
                    productId = productId,
                    statisticsDate = startDate,
                    dailySalesQuantity = 50,
                    dailySalesAmount = BigDecimal("500000"),
                    dailyOrderCount = 10,
                    dailyRefundQuantity = 2,
                    dailyRefundAmount = BigDecimal("20000"),
                    endOfDayStock = 100
                )
            )

            whenever(productStatisticsQueryService.getDailyStatistics(productId, startDate, endDate))
                .thenReturn(dailyList)

            val result: ApiResponse<List<ProductDailyStatisticsResponse>> =
                controller.getDailyStatistics(productId, startDate, endDate)

            assertEquals("SUCCESS", result.code)
            assertEquals(1, result.data!!.size)
            assertEquals(startDate, result.data!![0].statisticsDate)
            verify(productStatisticsQueryService).getDailyStatistics(productId, startDate, endDate)
        }
    }

    @Nested
    @DisplayName("getTopSellingProducts")
    inner class GetTopSellingProducts {
        @Test
        fun TOP_판매_조회_성공() {
            val topList = listOf(
                TopProductResponse(productId = 1L, totalAmount = BigDecimal("500000"), totalQuantity = 50)
            )

            whenever(productStatisticsQueryService.getTopSellingProducts(7, 10)).thenReturn(topList)

            val result: ApiResponse<List<TopProductResponse>> = controller.getTopSellingProducts(7, 10)

            assertEquals("SUCCESS", result.code)
            assertEquals(1, result.data!!.size)
            assertEquals(1L, result.data!![0].productId)
            verify(productStatisticsQueryService).getTopSellingProducts(7, 10)
        }
    }

    @Nested
    @DisplayName("getTopRefundProducts")
    inner class GetTopRefundProducts {
        @Test
        fun TOP_환불_조회_성공() {
            val topList = listOf(
                TopProductResponse(productId = 2L, totalAmount = BigDecimal("100000"), totalQuantity = 10)
            )

            whenever(productStatisticsQueryService.getTopRefundProducts(30, 5)).thenReturn(topList)

            val result: ApiResponse<List<TopProductResponse>> = controller.getTopRefundProducts(30, 5)

            assertEquals("SUCCESS", result.code)
            assertEquals(1, result.data!!.size)
            assertEquals(2L, result.data!![0].productId)
            verify(productStatisticsQueryService).getTopRefundProducts(30, 5)
        }
    }

    @Nested
    @DisplayName("getHourlyStatistics")
    inner class GetHourlyStatistics {
        @Test
        fun 시간대별_통계_조회_성공() {
            val productId = 1L
            val date = LocalDate.of(2026, 3, 13)
            val hourlyList = listOf(
                ProductHourlyStatisticsResponse(
                    productId = productId,
                    statisticsDate = date,
                    hour = 10,
                    hourlySalesQuantity = 20,
                    hourlySalesAmount = BigDecimal("200000"),
                    hourlyOrderCount = 5,
                    hourlyRefundQuantity = 0,
                    hourlyRefundAmount = BigDecimal.ZERO
                )
            )

            whenever(productStatisticsQueryService.getHourlyStatistics(productId, date))
                .thenReturn(hourlyList)

            val result: ApiResponse<List<ProductHourlyStatisticsResponse>> =
                controller.getHourlyStatistics(productId, date)

            assertEquals("SUCCESS", result.code)
            assertEquals(1, result.data!!.size)
            assertEquals(10, result.data!![0].hour)
            verify(productStatisticsQueryService).getHourlyStatistics(productId, date)
        }
    }

    @Nested
    @DisplayName("getPeakHours")
    inner class GetPeakHours {
        @Test
        fun 피크_타임_조회_성공() {
            val peakList = listOf(
                PeakHourResponse(hour = 14, totalSalesAmount = BigDecimal("1500000"), totalOrderCount = 75)
            )

            whenever(productStatisticsQueryService.getPeakHours(7)).thenReturn(peakList)

            val result: ApiResponse<List<PeakHourResponse>> = controller.getPeakHours(7)

            assertEquals("SUCCESS", result.code)
            assertEquals(1, result.data!!.size)
            assertEquals(14, result.data!![0].hour)
            verify(productStatisticsQueryService).getPeakHours(7)
        }
    }
}
