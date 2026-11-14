package com.hoppingmall.product.statistics.service

import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.service.BucketStockService
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.product.service.ProductStatisticsCommandService
import com.hoppingmall.product.statistics.port.CartItemQueryPort
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
@Profile("!test")
class ProductStatisticsScheduler(
    private val productStatisticsRepository: ProductStatisticsRepository,
    private val cartItemQueryPort: CartItemQueryPort,
    private val inventoryRepository: InventoryRepository,
    private val bucketStockService: BucketStockService,
    private val productStatisticsCommandService: ProductStatisticsCommandService,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(ProductStatisticsScheduler::class.java)

    @Scheduled(fixedDelay = 3600000)
    fun syncCartAndInventory() {
        logger.info("장바구니/재고 동기화 시작")

        val cartMap = cartItemQueryPort
            .aggregateCartByProduct()
            .associateBy { it.productId }

        var lastId = 0L
        var updatedCount = 0

        while (true) {
            val currentLastId = lastId
            val batchUpdated = transactionTemplate.execute {
                val ids = productStatisticsRepository.findIdsAfter(currentLastId, PageRequest.of(0, SYNC_BATCH_SIZE))
                val lockedStats = if (ids.isEmpty()) {
                    emptyList()
                } else {
                    productStatisticsRepository.findAllByIdInForUpdate(ids)
                }
                val productIds = lockedStats.map { it.productId }
                val stockMap = currentStocks(productIds)

                for (stats in lockedStats) {
                    val cart = cartMap[stats.productId]
                    stats.updateCartAndInventory(cart?.buyerCount ?: 0L, stockMap[stats.productId] ?: 0)
                }

                ids to lockedStats.size
            }!!

            updatedCount += batchUpdated.second
            if (batchUpdated.first.size < SYNC_BATCH_SIZE) break
            lastId = batchUpdated.first.last()
        }

        logger.info("장바구니/재고 동기화 완료: ${updatedCount}건")

        productStatisticsCommandService.flushHourlySnapshot()
    }

    @Scheduled(cron = "0 0 0 * * *")
    fun flushDailyStatistics() {
        logger.info("일별 통계 마감 시작")
        productStatisticsCommandService.flushDailySnapshot()
        logger.info("일별 통계 마감 완료")
    }

    private fun currentStocks(productIds: List<Long>): Map<Long, Int> =
        if (bucketStockService.enabled) {
            bucketStockService.totalStocks(productIds)
        } else {
            inventoryRepository.findAllByProductIdIn(productIds).associate { it.productId to it.stockQuantity }
        }

    companion object {
        private const val SYNC_BATCH_SIZE = 500
    }
}
