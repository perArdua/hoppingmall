package com.hoppingmall.order.port

interface PaymentCommandPort {
    fun cancelPayment(orderId: Long): PaymentCancellationResult
}

enum class PaymentCancellationResult {
    CANCELLED,
    NOT_FOUND,
    FAILED
}
