package com.hoppingmall.mall.refund.exception

import com.hoppingmall.mall.refund.exception.code.RefundErrorCode

class RefundInvalidStatusException : RefundException(RefundErrorCode.REFUND_INVALID_STATUS)
