package com.hoppingmall.payment.port

interface OrderCommandPort {
    fun cancelOrder(orderId: Long): Boolean
}
