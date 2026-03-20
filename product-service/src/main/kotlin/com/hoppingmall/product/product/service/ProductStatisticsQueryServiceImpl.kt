package com.hoppingmall.product.product.service

import com.hoppingmall.product.product.domain.repository.ProductDailyStatisticsRepository
import com.hoppingmall.product.product.domain.repository.ProductHourlyStatisticsRepository
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.product.dto.response.*
import com.hoppingmall.product.product.exception.ProductStatisticsNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class ProductStatisticsQueryServiceImpl(
    private val productStatisticsRepository: ProductStatisticsRepository,
    private val productDailyStatisticsRepository: ProductDailyStatisticsRepository,
    private val productHourlyStatisticsRepository: ProductHourlyStatisticsRepository
) : ProductStatisticsQueryService {

    override fun getAll(pageable: Pageable): Page<ProductStatisticsResponse> {
        return productStatisticsRepository.findAll(pageable)
            .map { ProductStatisticsResponse.from(it) }
    }

    override fun getByProductId(productId: Long): ProductStatisticsResponse {
        val statistics = productStatisticsRepository.findByProductId(productId)
            ?: throw ProductStatisticsNotFoundException()
        return ProductStatisticsResponse.from(statistics)
    }

    override fun getBySellerId(sellerId: Long, pageable: Pageable): Slice<ProductStatisticsResponse> {
        return productStatisticsRepository.findBySellerId(sellerId, pageable)
            .map { ProductStatisticsResponse.from(it) }
    }

    override fun getByCategoryId(categoryId: Long, pageable: Pageable): Slice<ProductStatisticsResponse> {
        return productStatisticsRepository.findByCategoryId(categoryId, pageable)
            .map { ProductStatisticsResponse.from(it) }
    }

    override fun getSummary(): ProductStatisticsSummaryResponse {
        return ProductStatisticsSummaryResponse(
            totalProductCount = productStatisticsRepository.countAllProducts(),
            totalSalesAmount = productStatisticsRepository.sumTotalSalesAmount(),
            totalRefundAmount = productStatisticsRepository.sumTotalRefundAmount(),
            averageRefundRate = productStatisticsRepository.avgRefundRate()
        )
    }

    override fun getTodaySummary(): TodaySummaryResponse {
        return TodaySummaryResponse(
            todaySalesAmount = productStatisticsRepository.sumTodaySalesAmount(),
            todayOrderCount = productStatisticsRepository.sumTodayOrderCount(),
            todayRefundAmount = productStatisticsRepository.sumTodayRefundAmount()
        )
    }

    override fun getDailyStatistics(
        productId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ProductDailyStatisticsResponse> {
        return productDailyStatisticsRepository
            .findByProductIdAndStatisticsDateBetweenOrderByStatisticsDateAsc(productId, startDate, endDate)
            .map { ProductDailyStatisticsResponse.from(it) }
    }

    override fun getTopSellingProducts(days: Int, limit: Int): List<TopProductResponse> {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong() - 1)
        return productDailyStatisticsRepository
            .findTopSellingProducts(startDate, endDate, limit)
            .map { TopProductResponse.from(it) }
    }

    override fun getTopRefundProducts(days: Int, limit: Int): List<TopProductResponse> {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong() - 1)
        return productDailyStatisticsRepository
            .findTopRefundProducts(startDate, endDate, limit)
            .map { TopProductResponse.from(it) }
    }

    override fun getHourlyStatistics(productId: Long, date: LocalDate): List<ProductHourlyStatisticsResponse> {
        return productHourlyStatisticsRepository
            .findByProductIdAndStatisticsDateOrderByHourAsc(productId, date)
            .map { ProductHourlyStatisticsResponse.from(it) }
    }

    override fun getPeakHours(days: Int): List<PeakHourResponse> {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong() - 1)
        return productHourlyStatisticsRepository
            .findPeakHours(startDate, endDate)
            .map { PeakHourResponse.from(it) }
    }
}
