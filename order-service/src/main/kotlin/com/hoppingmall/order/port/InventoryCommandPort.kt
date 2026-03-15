package com.hoppingmall.order.port

interface InventoryCommandPort {
    fun decreaseStock(productId: Long, quantity: Int)
    fun increaseStock(productId: Long, quantity: Int)
    fun reserveStock(productId: Long, quantity: Int): String
    fun confirmReservations(reservationIds: List<String>): Boolean
    fun cancelReservation(reservationId: String)
    fun cancelReservations(reservationIds: List<String>)
}
