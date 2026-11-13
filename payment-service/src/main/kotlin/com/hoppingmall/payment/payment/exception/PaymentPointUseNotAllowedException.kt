package com.hoppingmall.payment.payment.exception

import com.hoppingmall.payment.payment.exception.code.PaymentErrorCode

class PaymentPointUseNotAllowedException : PaymentException(PaymentErrorCode.PAYMENT_POINT_USE_NOT_ALLOWED)
