package com.hoppingmall.order.port

interface InventoryCommandPort {
    fun decreaseStock(productId: Long, quantity: Int)
    fun increaseStock(productId: Long, quantity: Int)
}
