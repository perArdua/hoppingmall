package com.hoppingmall.product.inventory.port

interface OrderTerminalCheckPort {
    fun terminalReservationIds(reservationIds: List<String>): List<String>
}
