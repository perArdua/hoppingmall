package com.hoppingmall.product.inventory.port

interface OrderTerminalCheckPort {
    fun check(reservationIds: List<String>): TerminalCheck

    data class TerminalCheck(
        val terminalReservationIds: List<String>,
        val cancelledReservationIds: List<String>?
    )
}
