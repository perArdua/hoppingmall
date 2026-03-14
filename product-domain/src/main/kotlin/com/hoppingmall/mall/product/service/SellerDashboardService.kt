package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.dto.response.ProductDailyStatisticsResponse
import com.hoppingmall.mall.product.dto.response.ProductHourlyStatisticsResponse
import com.hoppingmall.mall.product.dto.response.ProductStatisticsResponse
import com.hoppingmall.mall.product.dto.response.SellerTodaySummaryResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDate

interface SellerDashboardService {
    fun getOverview(sellerId: Long, pageable: Pageable): Page<ProductStatisticsResponse>
    fun getProductStatistics(sellerId: Long, productId: Long): ProductStatisticsResponse
    fun getTodaySummary(sellerId: Long): SellerTodaySummaryResponse
    fun getDailyStatistics(sellerId: Long, productId: Long, startDate: LocalDate, endDate: LocalDate): List<ProductDailyStatisticsResponse>
    fun getHourlyStatistics(sellerId: Long, productId: Long, date: LocalDate): List<ProductHourlyStatisticsResponse>
}
