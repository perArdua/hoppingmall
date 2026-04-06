package com.hoppingmall.order.port

interface PaymentCommandPort {
    fun cancelPayment(orderId: Long): Boolean
}
