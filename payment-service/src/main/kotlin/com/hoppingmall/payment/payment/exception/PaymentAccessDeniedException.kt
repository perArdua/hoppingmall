package com.hoppingmall.payment.payment.exception

import com.hoppingmall.payment.payment.exception.code.PaymentErrorCode

class PaymentAccessDeniedException : PaymentException(PaymentErrorCode.PAYMENT_ACCESS_DENIED)
