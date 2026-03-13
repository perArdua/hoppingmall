package com.hoppingmall.mall.inventory.api

interface InventoryCommandPort {
    fun decreaseStock(productId: Long, quantity: Int)
    fun increaseStock(productId: Long, quantity: Int)
}
