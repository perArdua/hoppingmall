package com.hoppingmall.product.statistics.port

interface CartItemQueryPort {
    fun aggregateCartByProduct(): List<CartAggregation>
}

data class CartAggregation(
    val productId: Long,
    val buyerCount: Long
)
