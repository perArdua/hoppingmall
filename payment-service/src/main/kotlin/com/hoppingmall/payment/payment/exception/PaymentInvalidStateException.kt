package com.hoppingmall.payment.payment.exception

import com.hoppingmall.payment.payment.exception.code.PaymentErrorCode

class PaymentInvalidStateException : PaymentException(PaymentErrorCode.PAYMENT_INVALID_STATE)
