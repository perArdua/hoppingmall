package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.cartItem.domain.CartItem
import java.math.BigDecimal

fun CartItem.Companion.fixture(
    buyerId: Long = 1L,
    productId: Long = 100L,
    productName: String = "테스트 상품",
    productPrice: BigDecimal = BigDecimal("15000"),
    productImageUrl: String? = "https://example.com/image.jpg",
    quantity: Int = 2
): CartItem {
    return CartItem.create(buyerId, productId, productName, productPrice, productImageUrl, quantity)
} 