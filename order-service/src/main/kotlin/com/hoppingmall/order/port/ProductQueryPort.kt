package com.hoppingmall.order.port

import java.math.BigDecimal

interface ProductQueryPort {
    fun findProductById(productId: Long): ProductInfo?
    fun findProductsByIds(productIds: List<Long>): List<ProductInfo>
}

data class ProductInfo(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val sellerId: Long,
    val imageUrl: String? = null,
    val status: String? = null,
    val stockQuantity: Int? = null
) {
    fun isOnSale(): Boolean = status != SOLD_OUT

    fun hasStock(quantity: Int): Boolean = stockQuantity == null || stockQuantity >= quantity

    companion object {
        const val SOLD_OUT = "SOLD_OUT"
    }
}
