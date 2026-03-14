package com.hoppingmall.order.refund.exception

import com.hoppingmall.order.refund.exception.code.RefundErrorCode

class RefundPaymentNotFoundException : RefundException(RefundErrorCode.REFUND_PAYMENT_NOT_FOUND)
