package com.hoppingmall.payment.payment.exception

import com.hoppingmall.payment.payment.exception.code.PaymentErrorCode

class PaymentOrderNotPayableException : PaymentException(PaymentErrorCode.PAYMENT_ORDER_NOT_PAYABLE)
