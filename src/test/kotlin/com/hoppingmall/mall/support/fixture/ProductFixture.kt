package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.global.enums.ProductStatus
import com.hoppingmall.mall.product.domain.Product

fun Product.Companion.fixture(
    sellerId: Long = 1L,
    name: String = "테스트 상품",
    description: String = "테스트 상품 설명",
    price: Long = 10000L,
    status: ProductStatus = ProductStatus.AVAILABLE
): Product {
    return Product.create(sellerId, name, description, price, status)
} 