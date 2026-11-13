package com.hoppingmall.payment.port

interface InventoryCommandPort {
    fun increaseStock(productId: Long, quantity: Int)

    fun increaseStock(productId: Long, quantity: Int, requestKey: String)
}
