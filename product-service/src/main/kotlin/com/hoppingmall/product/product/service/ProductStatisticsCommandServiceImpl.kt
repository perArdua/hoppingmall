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
import org.springframework.data.repository.findByIdOrNull
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
        if (productStatisticsRepository.addSales(productId, quantity, amount) == 0) {
            createStatistics(productId)
            productStatisticsRepository.addSales(productId, quantity, amount)
        }
        refreshDerivedMetrics(productId)
    }

    @Transactional
    override fun decrementSalesStats(productId: Long, quantity: Long, amount: BigDecimal) {
        if (productStatisticsRepository.subtractSales(productId, quantity, amount) == 0) return
        refreshDerivedMetrics(productId)
    }

    @Transactional
    override fun incrementRefundStats(productId: Long, quantity: Long, amount: BigDecimal) {
        if (productStatisticsRepository.addRefund(productId, quantity, amount) == 0) {
            createStatistics(productId)
            productStatisticsRepository.addRefund(productId, quantity, amount)
        }
        refreshDerivedMetrics(productId)
    }

    override fun flushDailySnapshot() {
        val statisticsDate = LocalDate.now().minusDays(CLOSED_DAY_OFFSET)
        var lastId = 0L
        var totalProcessed = 0

        while (true) {
            val chunkIds = productStatisticsRepository.findIdsAfter(lastId, PageRequest.of(0, BATCH_SIZE))
            if (chunkIds.isEmpty()) break

            transactionTemplate.executeWithoutResult {
                val lockedChunk = productStatisticsRepository.findAllByIdInForUpdate(chunkIds)
                val closedProductIds = productDailyStatisticsRepository
                    .findByStatisticsDateAndProductIdIn(statisticsDate, lockedChunk.map { it.productId })
                    .map { it.productId }
                    .toSet()
                val chunk = lockedChunk.filterNot { it.productId in closedProductIds }
                if (chunk.isEmpty()) return@executeWithoutResult
                val productIds = chunk.map { it.productId }

                val last7DaysMap = toSalesAmountMap(
                    productDailyStatisticsRepository.sumSalesAmountByProductIdsAndDateRange(productIds, statisticsDate.minusDays(6), statisticsDate)
                )
                val last30DaysMap = toSalesAmountMap(
                    productDailyStatisticsRepository.sumSalesAmountByProductIdsAndDateRange(productIds, statisticsDate.minusDays(29), statisticsDate)
                )
                val previousWeekMap = toSalesAmountMap(
                    productDailyStatisticsRepository.sumSalesAmountByProductIdsAndDateRange(productIds, statisticsDate.minusDays(13), statisticsDate.minusDays(7))
                )

                val dailyToSave = mutableListOf<ProductDailyStatistics>()
                for (stats in chunk) {
                    val daily = ProductDailyStatistics.create(
                        productId = stats.productId,
                        statisticsDate = statisticsDate
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

            totalProcessed += chunkIds.size
            if (chunkIds.size < BATCH_SIZE) break
            lastId = chunkIds.last()
        }

        logger.info("일별 통계 스냅샷 완료: ${totalProcessed}건")
    }

    override fun flushHourlySnapshot() {
        val now = java.time.LocalDateTime.now()
        val today = now.toLocalDate()
        val currentHour = now.hour
        var lastId = 0L
        var totalProcessed = 0

        while (true) {
            val chunk = productStatisticsRepository.findActiveAfter(lastId, PageRequest.of(0, BATCH_SIZE))
            if (chunk.isEmpty()) break

            transactionTemplate.executeWithoutResult {
                val productIds = chunk.map { it.productId }

                val existingHourlyMap = productHourlyStatisticsRepository
                    .findByStatisticsDateAndHourAndProductIdIn(today, currentHour, productIds)
                    .associateBy { it.productId }

                val hourlyToSave = mutableListOf<ProductHourlyStatistics>()
                for (stats in chunk) {
                    val hourly = existingHourlyMap[stats.productId] ?: ProductHourlyStatistics.create(
                        productId = stats.productId,
                        statisticsDate = today,
                        hour = currentHour
                    )
                    hourly.updateFromStatistics(stats)
                    hourlyToSave.add(hourly)
                }

                productHourlyStatisticsRepository.saveAll(hourlyToSave)
            }

            totalProcessed += chunk.size
            if (chunk.size < BATCH_SIZE) break
            lastId = checkNotNull(chunk.last().id)
        }

        logger.info("시간별 통계 스냅샷 완료: ${totalProcessed}건 (${currentHour}시)")
    }

    private fun toSalesAmountMap(rows: List<Array<Any>>): Map<Long, BigDecimal> {
        return rows.associate { (it[0] as Long) to (it[1] as BigDecimal) }
    }

    companion object {
        private const val BATCH_SIZE = 100
        private const val CLOSED_DAY_OFFSET = 1L
    }

    private fun refreshDerivedMetrics(productId: Long) {
        productStatisticsRepository.findByProductIdForUpdate(productId)?.refreshDerivedMetrics()
    }

    private fun createStatistics(productId: Long): ProductStatistics {
        val product = productRepository.findByIdOrNull(productId)
            ?: productRepository.findIncludingDeletedById(productId)
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
