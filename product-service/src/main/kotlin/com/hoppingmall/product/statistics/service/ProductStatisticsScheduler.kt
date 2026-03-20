package com.hoppingmall.product.statistics.service

import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
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

        var page = 0
        var updatedCount = 0
        var hasNext: Boolean

        do {
            val currentPage = page
            val batchUpdated = transactionTemplate.execute {
                val statsPage = productStatisticsRepository.findAll(PageRequest.of(currentPage, 500))
                val productIds = statsPage.content.map { it.productId }
                val inventoryMap = inventoryRepository.findAllByProductIdIn(productIds).associateBy { it.productId }

                for (stats in statsPage.content) {
                    val cart = cartMap[stats.productId]
                    val inventory = inventoryMap[stats.productId]
                    stats.updateCartAndInventory(cart?.buyerCount ?: 0L, inventory?.stockQuantity ?: 0)
                }

                statsPage.hasNext() to statsPage.content.size
            }!!

            hasNext = batchUpdated.first
            updatedCount += batchUpdated.second
            page++
        } while (hasNext)

        logger.info("장바구니/재고 동기화 완료: ${updatedCount}건")

        productStatisticsCommandService.flushHourlySnapshot()
    }

    @Scheduled(cron = "0 0 0 * * *")
    fun flushDailyStatistics() {
        logger.info("일별 통계 마감 시작")
        productStatisticsCommandService.flushDailySnapshot()
        logger.info("일별 통계 마감 완료")
    }
}
