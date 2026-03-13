package com.hoppingmall.mall.payment.exception

import com.hoppingmall.mall.payment.exception.code.PaymentErrorCode

class PaymentInvalidAmountException : PaymentException(PaymentErrorCode.PAYMENT_INVALID_AMOUNT) 