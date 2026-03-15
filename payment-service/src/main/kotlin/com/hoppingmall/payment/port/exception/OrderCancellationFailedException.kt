package com.hoppingmall.payment.port.exception

class OrderCancellationFailedException(orderId: Long, cause: Throwable) :
    RuntimeException("주문 취소 실패: orderId=$orderId", cause)
