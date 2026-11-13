package com.hoppingmall.payment.port

interface OrderSummaryQueryPort {
    fun findOrder(orderId: Long): OrderSummary?
}
