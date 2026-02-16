package com.hoppingmall.mall.support.fixture

import com.hoppingmall.mall.inventory.domain.Inventory

fun Inventory.Companion.fixture(
    productId: Long = 100L,
    stockQuantity: Int = 100
): Inventory {
    return Inventory.create(productId = productId, stockQuantity = stockQuantity)
}

fun Inventory.Companion.emptyStockFixture(
    productId: Long = 100L
): Inventory {
    return Inventory.create(productId = productId, stockQuantity = 0)
}

fun Inventory.Companion.lowStockFixture(
    productId: Long = 100L,
    stockQuantity: Int = 3
): Inventory {
    return Inventory.create(productId = productId, stockQuantity = stockQuantity)
}
