package com.hoppingmall.product.internal

import com.hoppingmall.product.product.domain.repository.ProductImageRepository
import com.hoppingmall.product.product.domain.repository.ProductRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/internal/api/v1/products")
class InternalProductController(
    private val productRepository: ProductRepository,
    private val productImageRepository: ProductImageRepository
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
                imageUrl = firstImageUrl
            )
        )
    }

    @GetMapping
    fun getProductsByIds(@RequestParam ids: List<Long>): ResponseEntity<List<ProductInfo>> {
        val products = productRepository.findAllById(ids)
        return ResponseEntity.ok(
            products.map { product ->
                ProductInfo(
                    id = product.id!!,
                    name = product.name,
                    price = product.price,
                    sellerId = product.sellerId
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
        val imageUrl: String? = null
    )
}
