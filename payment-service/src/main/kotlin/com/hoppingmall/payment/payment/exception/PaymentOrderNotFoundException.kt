package com.hoppingmall.payment.payment.exception

import com.hoppingmall.payment.payment.exception.code.PaymentErrorCode

class PaymentOrderNotFoundException : PaymentException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND)
