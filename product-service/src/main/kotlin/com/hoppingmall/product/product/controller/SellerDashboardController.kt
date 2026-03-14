package com.hoppingmall.product.product.controller

import com.hoppingmall.product.common.UserPrincipal
import com.hoppingmall.product.common.ApiResponse
import com.hoppingmall.product.product.dto.response.ProductDailyStatisticsResponse
import com.hoppingmall.product.product.dto.response.ProductHourlyStatisticsResponse
import com.hoppingmall.product.product.dto.response.ProductStatisticsResponse
import com.hoppingmall.product.product.dto.response.SellerTodaySummaryResponse
import com.hoppingmall.product.product.service.SellerDashboardService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/seller/dashboard")
@Tag(name = "판매자 대시보드")
class SellerDashboardController(
    private val sellerDashboardService: SellerDashboardService
) {

    @GetMapping("/overview")
    fun getOverview(
        @AuthenticationPrincipal principal: UserPrincipal,
        pageable: Pageable
    ): ApiResponse<Page<ProductStatisticsResponse>> {
        return ApiResponse.success(sellerDashboardService.getOverview(principal.getUserId(), pageable))
    }

    @GetMapping("/products/{productId}")
    fun getProductStatistics(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable productId: Long
    ): ApiResponse<ProductStatisticsResponse> {
        return ApiResponse.success(sellerDashboardService.getProductStatistics(principal.getUserId(), productId))
    }

    @GetMapping("/today")
    fun getTodaySummary(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<SellerTodaySummaryResponse> {
        return ApiResponse.success(sellerDashboardService.getTodaySummary(principal.getUserId()))
    }

    @GetMapping("/daily")
    fun getDailyStatistics(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam productId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate
    ): ApiResponse<List<ProductDailyStatisticsResponse>> {
        return ApiResponse.success(
            sellerDashboardService.getDailyStatistics(principal.getUserId(), productId, startDate, endDate)
        )
    }

    @GetMapping("/hourly")
    fun getHourlyStatistics(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam productId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ApiResponse<List<ProductHourlyStatisticsResponse>> {
        return ApiResponse.success(
            sellerDashboardService.getHourlyStatistics(principal.getUserId(), productId, date)
        )
    }
}
