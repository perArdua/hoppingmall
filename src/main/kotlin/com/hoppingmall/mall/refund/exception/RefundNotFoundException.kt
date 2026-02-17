package com.hoppingmall.mall.refund.exception

import com.hoppingmall.mall.refund.exception.code.RefundErrorCode

class RefundNotFoundException : RefundException(RefundErrorCode.REFUND_NOT_FOUND)
