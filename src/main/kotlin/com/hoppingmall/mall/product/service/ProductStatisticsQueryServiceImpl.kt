package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.domain.repository.ProductDailyStatisticsRepository
import com.hoppingmall.mall.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.mall.product.dto.response.*
import com.hoppingmall.mall.product.exception.ProductStatisticsNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class ProductStatisticsQueryServiceImpl(
    private val productStatisticsRepository: ProductStatisticsRepository,
    private val productDailyStatisticsRepository: ProductDailyStatisticsRepository
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

    override fun getBySellerId(sellerId: Long, pageable: Pageable): Page<ProductStatisticsResponse> {
        return productStatisticsRepository.findBySellerId(sellerId, pageable)
            .map { ProductStatisticsResponse.from(it) }
    }

    override fun getByCategoryId(categoryId: Long, pageable: Pageable): Page<ProductStatisticsResponse> {
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
}
