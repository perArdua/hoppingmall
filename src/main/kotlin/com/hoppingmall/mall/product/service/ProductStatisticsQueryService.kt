package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.dto.response.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDate

interface ProductStatisticsQueryService {
    fun getAll(pageable: Pageable): Page<ProductStatisticsResponse>
    fun getByProductId(productId: Long): ProductStatisticsResponse
    fun getBySellerId(sellerId: Long, pageable: Pageable): Page<ProductStatisticsResponse>
    fun getByCategoryId(categoryId: Long, pageable: Pageable): Page<ProductStatisticsResponse>
    fun getSummary(): ProductStatisticsSummaryResponse
    fun getTodaySummary(): TodaySummaryResponse
    fun getDailyStatistics(productId: Long, startDate: LocalDate, endDate: LocalDate): List<ProductDailyStatisticsResponse>
    fun getTopSellingProducts(days: Int, limit: Int): List<TopProductResponse>
    fun getTopRefundProducts(days: Int, limit: Int): List<TopProductResponse>
}
