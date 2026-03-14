package com.hoppingmall.order.port

import java.math.BigDecimal

interface ProductQueryPort {
    fun findProductById(productId: Long): ProductInfo?
    fun findProductsByIds(productIds: List<Long>): List<ProductInfo>
    fun findProductImageUrl(productId: Long): String?
}

data class ProductInfo(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val sellerId: Long
)
