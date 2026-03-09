package com.hoppingmall.mall.product.dto.response

import com.hoppingmall.mall.global.enums.ProductStatus
import com.hoppingmall.mall.product.domain.Product
import com.hoppingmall.mall.product.domain.ProductImage
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
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun from(product: Product, images: List<ProductImage>): ProductResponse {
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
                updatedAt = product.updatedAt
            )
        }
    }
}
