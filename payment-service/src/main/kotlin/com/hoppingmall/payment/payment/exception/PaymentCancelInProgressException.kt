package com.hoppingmall.payment.payment.exception

import com.hoppingmall.payment.payment.exception.code.PaymentErrorCode

class PaymentCancelInProgressException : PaymentException(PaymentErrorCode.PAYMENT_CANCEL_IN_PROGRESS)
