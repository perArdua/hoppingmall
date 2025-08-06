package com.hoppingmall.mall.payment.exception

import com.hoppingmall.mall.payment.exception.code.PaymentErrorCode

class PaymentInvalidStateException : PaymentException(PaymentErrorCode.PAYMENT_INVALID_STATE) 