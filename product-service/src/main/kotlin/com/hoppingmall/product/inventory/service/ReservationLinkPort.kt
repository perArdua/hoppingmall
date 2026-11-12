package com.hoppingmall.product.inventory.service

interface ReservationLinkPort {
    fun check(reservationIds: List<String>): LinkResult

    data class LinkResult(val linked: List<String>, val aborted: List<String>)
}
