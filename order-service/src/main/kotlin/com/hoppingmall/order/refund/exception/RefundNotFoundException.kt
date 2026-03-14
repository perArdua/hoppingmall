package com.hoppingmall.order.refund.exception

import com.hoppingmall.order.refund.exception.code.RefundErrorCode

class RefundNotFoundException : RefundException(RefundErrorCode.REFUND_NOT_FOUND)
