package com.hoppingmall.mall.product.controller

import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.product.dto.response.*
import com.hoppingmall.mall.product.service.ProductStatisticsQueryService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/admin/product-statistics")
class ProductStatisticsController(
    private val productStatisticsQueryService: ProductStatisticsQueryService
) {
    @GetMapping
    fun getAll(pageable: Pageable): ApiResponse<Page<ProductStatisticsResponse>> {
        return ApiResponse.success(productStatisticsQueryService.getAll(pageable))
    }

    @GetMapping("/{productId}")
    fun getByProductId(@PathVariable productId: Long): ApiResponse<ProductStatisticsResponse> {
        return ApiResponse.success(productStatisticsQueryService.getByProductId(productId))
    }

    @GetMapping("/seller/{sellerId}")
    fun getBySellerId(
        @PathVariable sellerId: Long,
        pageable: Pageable
    ): ApiResponse<Page<ProductStatisticsResponse>> {
        return ApiResponse.success(productStatisticsQueryService.getBySellerId(sellerId, pageable))
    }

    @GetMapping("/category/{categoryId}")
    fun getByCategoryId(
        @PathVariable categoryId: Long,
        pageable: Pageable
    ): ApiResponse<Page<ProductStatisticsResponse>> {
        return ApiResponse.success(productStatisticsQueryService.getByCategoryId(categoryId, pageable))
    }

    @GetMapping("/summary")
    fun getSummary(): ApiResponse<ProductStatisticsSummaryResponse> {
        return ApiResponse.success(productStatisticsQueryService.getSummary())
    }

    @GetMapping("/realtime/today")
    fun getTodaySummary(): ApiResponse<TodaySummaryResponse> {
        return ApiResponse.success(productStatisticsQueryService.getTodaySummary())
    }

    @GetMapping("/daily")
    fun getDailyStatistics(
        @RequestParam productId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate
    ): ApiResponse<List<ProductDailyStatisticsResponse>> {
        return ApiResponse.success(productStatisticsQueryService.getDailyStatistics(productId, startDate, endDate))
    }

    @GetMapping("/top-selling")
    fun getTopSellingProducts(
        @RequestParam(defaultValue = "7") days: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ApiResponse<List<TopProductResponse>> {
        return ApiResponse.success(productStatisticsQueryService.getTopSellingProducts(days, limit))
    }

    @GetMapping("/top-refund")
    fun getTopRefundProducts(
        @RequestParam(defaultValue = "7") days: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ApiResponse<List<TopProductResponse>> {
        return ApiResponse.success(productStatisticsQueryService.getTopRefundProducts(days, limit))
    }

    @GetMapping("/hourly")
    fun getHourlyStatistics(
        @RequestParam productId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ApiResponse<List<ProductHourlyStatisticsResponse>> {
        return ApiResponse.success(productStatisticsQueryService.getHourlyStatistics(productId, date))
    }

    @GetMapping("/peak-hours")
    fun getPeakHours(
        @RequestParam(defaultValue = "7") days: Int
    ): ApiResponse<List<PeakHourResponse>> {
        return ApiResponse.success(productStatisticsQueryService.getPeakHours(days))
    }
}
