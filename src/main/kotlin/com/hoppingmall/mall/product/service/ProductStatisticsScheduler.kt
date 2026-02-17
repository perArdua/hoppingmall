package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.mall.inventory.domain.repository.InventoryRepository
import com.hoppingmall.mall.order.domain.repository.OrderItemRepository
import com.hoppingmall.mall.product.domain.ProductStatistics
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.mall.refund.domain.repository.RefundItemRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
@Profile("!test")
class ProductStatisticsScheduler(
    private val productRepository: ProductRepository,
    private val productStatisticsRepository: ProductStatisticsRepository,
    private val orderItemRepository: OrderItemRepository,
    private val refundItemRepository: RefundItemRepository,
    private val cartItemRepository: CartItemRepository,
    private val inventoryRepository: InventoryRepository
) {
    private val logger = LoggerFactory.getLogger(ProductStatisticsScheduler::class.java)

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    fun aggregateProductStatistics() {
        logger.info("상품 통계 집계 시작")

        val products = productRepository.findAll()
        if (products.isEmpty()) {
            logger.info("집계할 상품이 없습니다")
            return
        }

        val salesMap = orderItemRepository
            .aggregateSalesByProduct(listOf("PAID", "SHIPPED", "DELIVERED"))
            .associateBy { it.getProductId() }

        val refundMap = refundItemRepository
            .aggregateRefundsByProduct("COMPLETED")
            .associateBy { it.getProductId() }

        val cartMap = cartItemRepository
            .aggregateCartByProduct()
            .associateBy { it.getProductId() }

        val inventoryMap = inventoryRepository.findAll()
            .associateBy { it.productId }

        var createdCount = 0
        var updatedCount = 0

        for (product in products) {
            val productId = product.id ?: continue
            val sales = salesMap[productId]
            val refund = refundMap[productId]
            val cart = cartMap[productId]
            val inventory = inventoryMap[productId]

            val totalSalesQuantity = sales?.getTotalQuantity() ?: 0L
            val totalSalesAmount = sales?.getTotalAmount() ?: BigDecimal.ZERO
            val totalRefundQuantity = refund?.getTotalQuantity() ?: 0L
            val totalRefundAmount = refund?.getTotalAmount() ?: BigDecimal.ZERO
            val currentCartCount = cart?.getBuyerCount() ?: 0L
            val currentStock = inventory?.stockQuantity ?: 0

            val existing = productStatisticsRepository.findByProductId(productId)
            if (existing != null) {
                existing.update(
                    productName = product.name,
                    categoryId = product.categoryId,
                    totalSalesQuantity = totalSalesQuantity,
                    totalSalesAmount = totalSalesAmount,
                    totalRefundQuantity = totalRefundQuantity,
                    totalRefundAmount = totalRefundAmount,
                    currentCartCount = currentCartCount,
                    currentStock = currentStock
                )
                updatedCount++
            } else {
                productStatisticsRepository.save(
                    ProductStatistics.create(
                        productId = productId,
                        productName = product.name,
                        sellerId = product.sellerId,
                        categoryId = product.categoryId,
                        totalSalesQuantity = totalSalesQuantity,
                        totalSalesAmount = totalSalesAmount,
                        totalRefundQuantity = totalRefundQuantity,
                        totalRefundAmount = totalRefundAmount,
                        currentCartCount = currentCartCount,
                        currentStock = currentStock
                    )
                )
                createdCount++
            }
        }

        logger.info("상품 통계 집계 완료 - 신규: ${createdCount}건, 갱신: ${updatedCount}건")
    }
}
