package com.hoppingmall.order.refund.exception

import com.hoppingmall.order.refund.exception.code.RefundErrorCode

class RefundInvalidStatusException : RefundException(RefundErrorCode.REFUND_INVALID_STATUS)
