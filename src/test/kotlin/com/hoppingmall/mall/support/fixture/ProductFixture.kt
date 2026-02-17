package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.global.enums.ProductStatus
import com.hoppingmall.mall.product.domain.Product
import java.math.BigDecimal

fun Product.Companion.fixture(
    sellerId: Long = 1L,
    name: String = "테스트 상품",
    description: String = "테스트 상품 설명",
    price: BigDecimal = BigDecimal("10000"),
    status: ProductStatus = ProductStatus.AVAILABLE
): Product {
    return Product.create(sellerId, name, description, price, status)
} 