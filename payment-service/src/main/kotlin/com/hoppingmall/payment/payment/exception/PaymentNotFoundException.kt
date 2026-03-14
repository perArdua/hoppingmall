package com.hoppingmall.payment.payment.exception

import com.hoppingmall.payment.payment.exception.code.PaymentErrorCode

class PaymentNotFoundException : PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND)
