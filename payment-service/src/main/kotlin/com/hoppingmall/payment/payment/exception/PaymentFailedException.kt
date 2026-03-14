package com.hoppingmall.payment.payment.exception

import com.hoppingmall.payment.payment.exception.code.PaymentErrorCode

class PaymentFailedException : PaymentException(PaymentErrorCode.PAYMENT_FAILED)
