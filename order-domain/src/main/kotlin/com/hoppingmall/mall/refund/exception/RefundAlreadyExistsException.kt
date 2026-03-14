package com.hoppingmall.mall.refund.exception

import com.hoppingmall.mall.refund.exception.code.RefundErrorCode

class RefundAlreadyExistsException : RefundException(RefundErrorCode.REFUND_ALREADY_EXISTS)
