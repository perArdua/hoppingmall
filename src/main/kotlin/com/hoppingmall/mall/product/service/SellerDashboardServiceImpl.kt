package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.domain.repository.ProductDailyStatisticsRepository
import com.hoppingmall.mall.product.domain.repository.ProductHourlyStatisticsRepository
import com.hoppingmall.mall.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.mall.product.dto.response.ProductDailyStatisticsResponse
import com.hoppingmall.mall.product.dto.response.ProductHourlyStatisticsResponse
import com.hoppingmall.mall.product.dto.response.ProductStatisticsResponse
import com.hoppingmall.mall.product.dto.response.SellerTodaySummaryResponse
import com.hoppingmall.mall.product.exception.ProductException
import com.hoppingmall.mall.product.exception.ProductStatisticsNotFoundException
import com.hoppingmall.mall.product.exception.code.ProductErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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

    override fun getOverview(sellerId: Long, pageable: Pageable): Page<ProductStatisticsResponse> {
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
        val topSelling = productStatisticsRepository.findTopSellingBySellerId(sellerId)
        return SellerTodaySummaryResponse(
            totalProducts = productStatisticsRepository.countBySellerId(sellerId),
            todaySalesAmount = productStatisticsRepository.sumTodaySalesAmountBySellerId(sellerId),
            todayOrderCount = productStatisticsRepository.sumTodayOrderCountBySellerId(sellerId),
            todayRefundAmount = productStatisticsRepository.sumTodayRefundAmountBySellerId(sellerId),
            topSellingProductId = topSelling?.productId,
            topSellingProductName = topSelling?.productName
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
