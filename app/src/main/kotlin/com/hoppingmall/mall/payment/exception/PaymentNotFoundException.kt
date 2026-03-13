package com.hoppingmall.mall.payment.exception

import com.hoppingmall.mall.payment.exception.code.PaymentErrorCode

class PaymentNotFoundException : PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND) 