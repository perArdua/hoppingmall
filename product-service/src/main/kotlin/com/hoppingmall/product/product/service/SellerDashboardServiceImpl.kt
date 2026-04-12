package com.hoppingmall.product.product.service

import com.hoppingmall.product.product.domain.repository.ProductDailyStatisticsRepository
import com.hoppingmall.product.product.domain.repository.ProductHourlyStatisticsRepository
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.product.dto.response.ProductDailyStatisticsResponse
import com.hoppingmall.product.product.dto.response.ProductHourlyStatisticsResponse
import com.hoppingmall.product.product.dto.response.ProductStatisticsResponse
import com.hoppingmall.product.product.dto.response.SellerTodaySummaryResponse
import com.hoppingmall.product.product.exception.ProductException
import com.hoppingmall.product.product.exception.ProductStatisticsNotFoundException
import com.hoppingmall.product.product.exception.code.ProductErrorCode
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class SellerDashboardServiceImpl(
    private val productStatisticsRepository: ProductStatisticsRepository,
    private val productDailyStatisticsRepository: ProductDailyStatisticsRepository,
    private val productHourlyStatisticsRepository: ProductHourlyStatisticsRepository
) : SellerDashboardService {

    override fun getOverview(sellerId: Long, pageable: Pageable): Slice<ProductStatisticsResponse> {
        return productStatisticsRepository.findBySellerId(sellerId, pageable)
            .map { ProductStatisticsResponse.from(it) }
    }

    override fun getProductStatistics(sellerId: Long, productId: Long): ProductStatisticsResponse {
        val stats = productStatisticsRepository.findByProductId(productId)
            ?: throw ProductStatisticsNotFoundException()
        if (stats.sellerId != sellerId) {
            throw ProductException(ProductErrorCode.SELLER_STATISTICS_ACCESS_DENIED)
        }
        return ProductStatisticsResponse.from(stats)
    }

    override fun getTodaySummary(sellerId: Long): SellerTodaySummaryResponse {
        val summary = productStatisticsRepository.findSellerTodaySummary(sellerId)
        val topSellingDto = productStatisticsRepository.findTopSellingBySellerId(sellerId)
        return SellerTodaySummaryResponse(
            totalProducts = summary.getTotalProducts(),
            todaySalesAmount = summary.getTodaySalesAmount(),
            todayOrderCount = summary.getTodayOrderCount(),
            todayRefundAmount = summary.getTodayRefundAmount(),
            topSellingProductId = topSellingDto?.productId,
            topSellingProductName = topSellingDto?.productName
        )
    }

    override fun getDailyStatistics(
        sellerId: Long,
        productId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ProductDailyStatisticsResponse> {
        validateProductOwnership(sellerId, productId)
        return productDailyStatisticsRepository
            .findByProductIdAndStatisticsDateBetweenOrderByStatisticsDateAsc(productId, startDate, endDate)
            .map { ProductDailyStatisticsResponse.from(it) }
    }

    override fun getHourlyStatistics(
        sellerId: Long,
        productId: Long,
        date: LocalDate
    ): List<ProductHourlyStatisticsResponse> {
        validateProductOwnership(sellerId, productId)
        return productHourlyStatisticsRepository
            .findByProductIdAndStatisticsDateOrderByHourAsc(productId, date)
            .map { ProductHourlyStatisticsResponse.from(it) }
    }

    private fun validateProductOwnership(sellerId: Long, productId: Long) {
        val stats = productStatisticsRepository.findByProductId(productId)
            ?: throw ProductStatisticsNotFoundException()
        if (stats.sellerId != sellerId) {
            throw ProductException(ProductErrorCode.SELLER_STATISTICS_ACCESS_DENIED)
        }
    }
}
