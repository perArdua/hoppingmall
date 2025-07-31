package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.product.domain.ProductImage

fun ProductImage.Companion.fixture(
    productId: Long = 1L,
    imageUrl: String? = "https://example.com/default-image.jpg"
): ProductImage {
    return ProductImage.create(productId, imageUrl ?: "https://example.com/default-image.jpg")
} 