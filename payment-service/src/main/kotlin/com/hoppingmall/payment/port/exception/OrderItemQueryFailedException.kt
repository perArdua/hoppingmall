package com.hoppingmall.payment.port.exception

class OrderItemQueryFailedException(orderId: Long, cause: Throwable) :
    RuntimeException("주문 상품 조회 실패: orderId=$orderId", cause)
