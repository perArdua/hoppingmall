package com.hoppingmall.product.product.dto.response

import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.ProductImage
import java.math.BigDecimal
import java.time.LocalDateTime

data class ProductResponse(
    val id: Long,
    val sellerId: Long,
    val categoryId: Long,
    val name: String,
    val description: String,
    val price: BigDecimal,
    val status: ProductStatus,
    val imageUrls: List<String>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?,
    val categoryName: String? = null,
    val averageRating: BigDecimal = BigDecimal.ZERO,
    val reviewCount: Long = 0,
    val inStock: Boolean? = null,
    val stockQuantity: Int? = null
) {
    companion object {
        fun from(product: Product, images: List<ProductImage>, categoryName: String?): ProductResponse {
            return ProductResponse(
                id = product.id!!,
                sellerId = product.sellerId,
                categoryId = product.categoryId,
                name = product.name,
                description = product.description,
                price = product.price,
                status = product.status,
                imageUrls = images.sortedBy { it.sortOrder }.map { it.imageUrl },
                createdAt = product.createdAt,
                updatedAt = product.updatedAt,
                categoryName = categoryName,
                averageRating = product.averageRating,
                reviewCount = product.reviewCount
            )
        }
    }
}
