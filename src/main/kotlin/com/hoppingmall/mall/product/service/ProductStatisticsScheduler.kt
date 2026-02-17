package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.mall.inventory.domain.repository.InventoryRepository
import com.hoppingmall.mall.product.domain.repository.ProductStatisticsRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Profile("!test")
class ProductStatisticsScheduler(
    private val productStatisticsRepository: ProductStatisticsRepository,
    private val cartItemRepository: CartItemRepository,
    private val inventoryRepository: InventoryRepository,
    private val productStatisticsCommandService: ProductStatisticsCommandService
) {
    private val logger = LoggerFactory.getLogger(ProductStatisticsScheduler::class.java)

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    fun syncCartAndInventory() {
        logger.info("장바구니/재고 동기화 시작")

        val allStats = productStatisticsRepository.findAll()
        if (allStats.isEmpty()) {
            logger.info("동기화할 통계가 없습니다")
            return
        }

        val cartMap = cartItemRepository
            .aggregateCartByProduct()
            .associateBy { it.getProductId() }

        val inventoryMap = inventoryRepository.findAll()
            .associateBy { it.productId }

        var updatedCount = 0

        for (stats in allStats) {
            val cart = cartMap[stats.productId]
            val inventory = inventoryMap[stats.productId]

            val cartCount = cart?.getBuyerCount() ?: 0L
            val stock = inventory?.stockQuantity ?: 0

            stats.updateCartAndInventory(cartCount, stock)
            updatedCount++
        }

        logger.info("장바구니/재고 동기화 완료: ${updatedCount}건")
    }

    @Scheduled(cron = "0 0 0 * * *")
    fun flushDailyStatistics() {
        logger.info("일별 통계 마감 시작")
        productStatisticsCommandService.flushDailySnapshot()
        logger.info("일별 통계 마감 완료")
    }
}
