package com.hoppingmall.product.product.service

import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.service.BucketStockService
import com.hoppingmall.product.product.dto.response.ProductResponse
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class ProductStockReader(
    private val inventoryRepository: InventoryRepository,
    private val bucketStockService: BucketStockService
) {

    fun stocks(productIds: Collection<Long>): Map<Long, Int> {
        if (productIds.isEmpty()) return emptyMap()
        val active = inventoryRepository.findAllByProductIdIn(productIds.distinct()).filter { it.deletedAt == null }
        if (!bucketStockService.enabled) return active.associate { it.productId to it.stockQuantity }
        val totals = bucketStockService.totalStocks(active.map { it.productId })
        return active.associate { it.productId to (totals[it.productId] ?: 0) }
    }

    fun withInStock(response: ProductResponse): ProductResponse =
        response.copy(inStock = (stocks(listOf(response.id))[response.id] ?: 0) > 0)
}
