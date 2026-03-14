package com.hoppingmall.payment.payment.exception

import com.hoppingmall.payment.payment.exception.code.PaymentErrorCode

class PaymentInvalidAmountException : PaymentException(PaymentErrorCode.PAYMENT_INVALID_AMOUNT)
