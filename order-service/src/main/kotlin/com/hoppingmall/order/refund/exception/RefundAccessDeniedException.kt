package com.hoppingmall.order.refund.exception

import com.hoppingmall.order.refund.exception.code.RefundErrorCode

class RefundAccessDeniedException : RefundException(RefundErrorCode.REFUND_ACCESS_DENIED)
