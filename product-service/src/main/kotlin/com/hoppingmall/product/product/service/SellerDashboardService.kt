package com.hoppingmall.product.product.service

import com.hoppingmall.product.product.dto.response.ProductDailyStatisticsResponse
import com.hoppingmall.product.product.dto.response.ProductHourlyStatisticsResponse
import com.hoppingmall.product.product.dto.response.ProductStatisticsResponse
import com.hoppingmall.product.product.dto.response.SellerTodaySummaryResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import java.time.LocalDate

interface SellerDashboardService {
    fun getOverview(sellerId: Long, pageable: Pageable): Slice<ProductStatisticsResponse>
    fun getProductStatistics(sellerId: Long, productId: Long): ProductStatisticsResponse
    fun getTodaySummary(sellerId: Long): SellerTodaySummaryResponse
    fun getDailyStatistics(sellerId: Long, productId: Long, startDate: LocalDate, endDate: LocalDate): List<ProductDailyStatisticsResponse>
    fun getHourlyStatistics(sellerId: Long, productId: Long, date: LocalDate): List<ProductHourlyStatisticsResponse>
}
