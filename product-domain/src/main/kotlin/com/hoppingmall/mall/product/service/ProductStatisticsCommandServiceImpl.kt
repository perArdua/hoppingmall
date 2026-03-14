package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.domain.ProductDailyStatistics
import com.hoppingmall.mall.product.domain.ProductHourlyStatistics
import com.hoppingmall.mall.product.domain.ProductStatistics
import com.hoppingmall.mall.product.domain.repository.ProductDailyStatisticsRepository
import com.hoppingmall.mall.product.domain.repository.ProductHourlyStatisticsRepository
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.domain.repository.ProductStatisticsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
@Transactional
class ProductStatisticsCommandServiceImpl(
    private val productStatisticsRepository: ProductStatisticsRepository,
    private val productDailyStatisticsRepository: ProductDailyStatisticsRepository,
    private val productHourlyStatisticsRepository: ProductHourlyStatisticsRepository,
    private val productRepository: ProductRepository
) : ProductStatisticsCommandService {

    private val logger = LoggerFactory.getLogger(ProductStatisticsCommandServiceImpl::class.java)

    override fun incrementSalesStats(productId: Long, quantity: Long, amount: BigDecimal) {
        val stats = getOrCreateStatistics(productId)
        stats.incrementSales(quantity, amount)
        productStatisticsRepository.save(stats)
    }

    override fun decrementSalesStats(productId: Long, quantity: Long, amount: BigDecimal) {
        val stats = productStatisticsRepository.findByProductId(productId) ?: return
        stats.decrementSales(quantity, amount)
        productStatisticsRepository.save(stats)
    }

    override fun incrementRefundStats(productId: Long, quantity: Long, amount: BigDecimal) {
        val stats = getOrCreateStatistics(productId)
        stats.incrementRefund(quantity, amount)
        productStatisticsRepository.save(stats)
    }

    override fun flushDailySnapshot() {
        val today = LocalDate.now()
        val allStats = productStatisticsRepository.findAll()

        for (stats in allStats) {
            val existing = productDailyStatisticsRepository
                .findByProductIdAndStatisticsDate(stats.productId, today)

            val daily = existing ?: ProductDailyStatistics.create(
                productId = stats.productId,
                statisticsDate = today
            )
            daily.updateFromStatistics(stats)
            productDailyStatisticsRepository.save(daily)

            val last7Days = productDailyStatisticsRepository.sumSalesAmountByProductIdAndDateRange(
                stats.productId, today.minusDays(6), today
            )
            val last30Days = productDailyStatisticsRepository.sumSalesAmountByProductIdAndDateRange(
                stats.productId, today.minusDays(29), today
            )
            val previousWeek = productDailyStatisticsRepository.sumSalesAmountByProductIdAndDateRange(
                stats.productId, today.minusDays(13), today.minusDays(7)
            )

            stats.updatePeriodMetrics(last7Days, last30Days, previousWeek)
            stats.resetToday()
            productStatisticsRepository.save(stats)
        }

        logger.info("일별 통계 스냅샷 완료: ${allStats.size}건")
    }

    override fun flushHourlySnapshot() {
        val now = java.time.LocalDateTime.now()
        val today = now.toLocalDate()
        val currentHour = now.hour
        val allStats = productStatisticsRepository.findAll()

        var flushedCount = 0
        for (stats in allStats) {
            if (stats.todaySalesQuantity == 0L && stats.todayRefundQuantity == 0L) continue

            val existing = productHourlyStatisticsRepository
                .findByProductIdAndStatisticsDateAndHour(stats.productId, today, currentHour)

            val hourly = existing ?: ProductHourlyStatistics.create(
                productId = stats.productId,
                statisticsDate = today,
                hour = currentHour
            )
            hourly.updateFromStatistics(stats)
            productHourlyStatisticsRepository.save(hourly)
            flushedCount++
        }

        logger.info("시간별 통계 스냅샷 완료: ${flushedCount}건 (${currentHour}시)")
    }

    private fun getOrCreateStatistics(productId: Long): ProductStatistics {
        val existing = productStatisticsRepository.findByProductId(productId)
        if (existing != null) return existing

        val product = productRepository.findById(productId).orElse(null)
            ?: throw IllegalArgumentException("상품을 찾을 수 없습니다: $productId")

        return productStatisticsRepository.save(
            ProductStatistics.create(
                productId = productId,
                productName = product.name,
                sellerId = product.sellerId,
                categoryId = product.categoryId
            )
        )
    }
}
