package com.hoppingmall.mall.payment.exception

import com.hoppingmall.mall.payment.exception.code.PaymentErrorCode

class PaymentAccessDeniedException : PaymentException(PaymentErrorCode.PAYMENT_ACCESS_DENIED)
