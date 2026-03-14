package com.hoppingmall.order.refund.exception

import com.hoppingmall.order.refund.exception.code.RefundErrorCode

class RefundAlreadyExistsException : RefundException(RefundErrorCode.REFUND_ALREADY_EXISTS)
