package com.hoppingmall.mall.payment.exception

import com.hoppingmall.mall.payment.exception.code.PaymentErrorCode

class PaymentFailedException : PaymentException(PaymentErrorCode.PAYMENT_FAILED) 