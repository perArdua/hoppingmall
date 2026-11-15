package com.hoppingmall.product.internal

import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.product.domain.repository.ProductImageRepository
import com.hoppingmall.product.product.domain.repository.ProductRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/internal/api/v1/products")
class InternalProductController(
    private val productRepository: ProductRepository,
    private val productImageRepository: ProductImageRepository,
    private val inventoryRepository: InventoryRepository
) {

    @GetMapping("/{id}")
    fun getProduct(@PathVariable id: Long): ResponseEntity<ProductInfo> {
        val product = productRepository.findNullableById(id)
            ?: return ResponseEntity.notFound().build()
        val firstImageUrl = productImageRepository.findByProductIdOrderBySortOrder(id)
            .firstOrNull()?.imageUrl
        return ResponseEntity.ok(
            ProductInfo(
                id = product.id!!,
                name = product.name,
                price = product.price,
                sellerId = product.sellerId,
                imageUrl = firstImageUrl,
                status = product.status.name,
                stockQuantity = inventoryRepository.findByProductId(id)?.stockQuantity ?: 0,
                withdrawalRestricted = product.withdrawalRestricted,
                withdrawalRestrictionReason = product.withdrawalRestrictionReason?.name
            )
        )
    }

    @GetMapping
    fun getProductsByIds(@RequestParam ids: List<Long>): ResponseEntity<List<ProductInfo>> {
        val products = productRepository.findAllById(ids)
        val stocks = inventoryRepository.findAllByProductIdIn(ids).associate { it.productId to it.stockQuantity }
        return ResponseEntity.ok(
            products.map { product ->
                ProductInfo(
                    id = product.id!!,
                    name = product.name,
                    price = product.price,
                    sellerId = product.sellerId,
                    status = product.status.name,
                    stockQuantity = stocks[product.id] ?: 0,
                    withdrawalRestricted = product.withdrawalRestricted,
                    withdrawalRestrictionReason = product.withdrawalRestrictionReason?.name
                )
            }
        )
    }

    @GetMapping("/{id}/image-url")
    fun getProductImageUrl(@PathVariable id: Long): ResponseEntity<String> {
        val images = productImageRepository.findByProductIdOrderBySortOrder(id)
        return ResponseEntity.ok(images.firstOrNull()?.imageUrl ?: "")
    }

    data class ProductInfo(
        val id: Long,
        val name: String,
        val price: BigDecimal,
        val sellerId: Long,
        val imageUrl: String? = null,
        val status: String,
        val stockQuantity: Int,
        val withdrawalRestricted: Boolean = false,
        val withdrawalRestrictionReason: String? = null
    )
}
