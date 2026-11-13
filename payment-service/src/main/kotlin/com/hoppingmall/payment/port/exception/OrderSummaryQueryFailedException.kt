package com.hoppingmall.payment.port.exception

class OrderSummaryQueryFailedException(orderId: Long, cause: Throwable) :
    RuntimeException("주문 조회 실패: orderId=$orderId", cause)
