package com.hoppingmall.payment.payment.exception

import com.hoppingmall.payment.payment.exception.code.PaymentErrorCode

class PaymentDuplicateException : PaymentException(PaymentErrorCode.PAYMENT_DUPLICATE)
