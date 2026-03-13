package com.hoppingmall.mall.refund.exception

import com.hoppingmall.mall.refund.exception.code.RefundErrorCode

class RefundAccessDeniedException : RefundException(RefundErrorCode.REFUND_ACCESS_DENIED)
