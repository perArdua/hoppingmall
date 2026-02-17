package com.hoppingmall.mall.product.dto.response

import com.hoppingmall.mall.global.enums.ProductStatus
import com.hoppingmall.mall.product.domain.Product
import com.hoppingmall.mall.product.domain.ProductImage
import java.math.BigDecimal
import java.time.LocalDateTime

data class ProductResponse(
    val id: Long,
    val sellerId: Long,
    val name: String,
    val description: String,
    val price: BigDecimal,
    val status: ProductStatus,
    val imageUrl: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun from(product: Product, image: ProductImage?): ProductResponse {
            return ProductResponse(
                id = product.id!!,
                sellerId = product.sellerId,
                name = product.name,
                description = product.description,
                price = product.price,
                status = product.status,
                imageUrl = image?.imageUrl,
                createdAt = product.createdAt,
                updatedAt = product.updatedAt
            )
        }
    }
}  