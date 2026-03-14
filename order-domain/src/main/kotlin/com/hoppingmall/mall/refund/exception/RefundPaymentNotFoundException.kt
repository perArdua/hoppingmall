package com.hoppingmall.mall.refund.exception

import com.hoppingmall.mall.refund.exception.code.RefundErrorCode

class RefundPaymentNotFoundException : RefundException(RefundErrorCode.REFUND_PAYMENT_NOT_FOUND)
