package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.product.domain.ProductStatistics
import java.math.BigDecimal

fun ProductStatistics.Companion.fixture(
    productId: Long = 1L,
    productName: String = "테스트 상품",
    sellerId: Long = 1L,
    categoryId: Long = 1L,
    totalSalesQuantity: Long = 100,
    totalSalesAmount: BigDecimal = BigDecimal("1000000"),
    totalRefundQuantity: Long = 5,
    totalRefundAmount: BigDecimal = BigDecimal("50000"),
    currentCartCount: Long = 10,
    currentStock: Int = 50
): ProductStatistics {
    return ProductStatistics.create(
        productId = productId,
        productName = productName,
        sellerId = sellerId,
        categoryId = categoryId,
        totalSalesQuantity = totalSalesQuantity,
        totalSalesAmount = totalSalesAmount,
        totalRefundQuantity = totalRefundQuantity,
        totalRefundAmount = totalRefundAmount,
        currentCartCount = currentCartCount,
        currentStock = currentStock
    )
}
