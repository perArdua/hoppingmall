package com.hoppingmall.product.product.service

import com.hoppingmall.product.product.domain.ProductDailyStatistics
import com.hoppingmall.product.product.domain.ProductHourlyStatistics
import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.domain.repository.ProductDailyStatisticsRepository
import com.hoppingmall.product.product.domain.repository.ProductHourlyStatisticsRepository
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate

@Service
class ProductStatisticsCommandServiceImpl(
    private val productStatisticsRepository: ProductStatisticsRepository,
    private val productDailyStatisticsRepository: ProductDailyStatisticsRepository,
    private val productHourlyStatisticsRepository: ProductHourlyStatisticsRepository,
    private val productRepository: ProductRepository,
    private val transactionTemplate: TransactionTemplate
) : ProductStatisticsCommandService {

    private val logger = LoggerFactory.getLogger(ProductStatisticsCommandServiceImpl::class.java)

    @Transactional
    override fun incrementSalesStats(productId: Long, quantity: Long, amount: BigDecimal) {
        val stats = getOrCreateStatistics(productId)
        stats.incrementSales(quantity, amount)
        productStatisticsRepository.save(stats)
    }

    @Transactional
    override fun decrementSalesStats(productId: Long, quantity: Long, amount: BigDecimal) {
        val stats = productStatisticsRepository.findByProductId(productId) ?: return
        stats.decrementSales(quantity, amount)
        productStatisticsRepository.save(stats)
    }

    @Transactional
    override fun incrementRefundStats(productId: Long, quantity: Long, amount: BigDecimal) {
        val stats = getOrCreateStatistics(productId)
        stats.incrementRefund(quantity, amount)
        productStatisticsRepository.save(stats)
    }

    override fun flushDailySnapshot() {
        val today = LocalDate.now()
        var page = 0
        var totalProcessed = 0

        while (true) {
            val slice = productStatisticsRepository.findAll(
                PageRequest.of(page, BATCH_SIZE, Sort.by("id"))
            )
            if (slice.isEmpty) break

            val chunk = slice.content
            transactionTemplate.executeWithoutResult {
                val productIds = chunk.map { it.productId }

                val existingDailyMap = productDailyStatisticsRepository
                    .findByStatisticsDateAndProductIdIn(today, productIds)
                    .associateBy { it.productId }

                val last7DaysMap = toSalesAmountMap(
                    productDailyStatisticsRepository.sumSalesAmountByProductIdsAndDateRange(productIds, today.minusDays(6), today)
                )
                val last30DaysMap = toSalesAmountMap(
                    productDailyStatisticsRepository.sumSalesAmountByProductIdsAndDateRange(productIds, today.minusDays(29), today)
                )
                val previousWeekMap = toSalesAmountMap(
                    productDailyStatisticsRepository.sumSalesAmountByProductIdsAndDateRange(productIds, today.minusDays(13), today.minusDays(7))
                )

                val dailyToSave = mutableListOf<ProductDailyStatistics>()
                for (stats in chunk) {
                    val daily = existingDailyMap[stats.productId] ?: ProductDailyStatistics.create(
                        productId = stats.productId,
                        statisticsDate = today
                    )
                    daily.updateFromStatistics(stats)
                    dailyToSave.add(daily)

                    stats.updatePeriodMetrics(
                        last7DaysMap[stats.productId] ?: BigDecimal.ZERO,
                        last30DaysMap[stats.productId] ?: BigDecimal.ZERO,
                        previousWeekMap[stats.productId] ?: BigDecimal.ZERO
                    )
                    stats.resetToday()
                }

                productDailyStatisticsRepository.saveAll(dailyToSave)
                productStatisticsRepository.saveAll(chunk)
            }

            totalProcessed += chunk.size
            if (!slice.hasNext()) break
            page++
        }

        logger.info("일별 통계 스냅샷 완료: ${totalProcessed}건")
    }

    @Transactional
    override fun flushHourlySnapshot() {
        val now = java.time.LocalDateTime.now()
        val today = now.toLocalDate()
        val currentHour = now.hour
        val activeStats = productStatisticsRepository.findAllActive()
        if (activeStats.isEmpty()) {
            logger.info("시간별 통계 스냅샷 완료: 0건 (${currentHour}시)")
            return
        }

        val productIds = activeStats.map { it.productId }
        val existingHourlyMap = productHourlyStatisticsRepository
            .findByStatisticsDateAndHourAndProductIdIn(today, currentHour, productIds)
            .associateBy { it.productId }

        val hourlyToSave = mutableListOf<ProductHourlyStatistics>()
        for (stats in activeStats) {
            val hourly = existingHourlyMap[stats.productId] ?: ProductHourlyStatistics.create(
                productId = stats.productId,
                statisticsDate = today,
                hour = currentHour
            )
            hourly.updateFromStatistics(stats)
            hourlyToSave.add(hourly)
        }

        productHourlyStatisticsRepository.saveAll(hourlyToSave)

        logger.info("시간별 통계 스냅샷 완료: ${hourlyToSave.size}건 (${currentHour}시)")
    }

    private fun toSalesAmountMap(rows: List<Array<Any>>): Map<Long, BigDecimal> {
        return rows.associate { (it[0] as Long) to (it[1] as BigDecimal) }
    }

    companion object {
        private const val BATCH_SIZE = 100
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
