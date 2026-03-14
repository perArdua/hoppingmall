package com.hoppingmall.order.refund.exception

import com.hoppingmall.order.common.BusinessException
import com.hoppingmall.order.refund.exception.code.RefundErrorCode

open class RefundException(
    errorCode: RefundErrorCode
) : BusinessException(errorCode)
