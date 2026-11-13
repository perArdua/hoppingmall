package com.hoppingmall.payment.payment.exception

import com.hoppingmall.payment.payment.exception.code.PaymentErrorCode

class PaymentOrderUnavailableException : PaymentException(PaymentErrorCode.PAYMENT_ORDER_UNAVAILABLE)
