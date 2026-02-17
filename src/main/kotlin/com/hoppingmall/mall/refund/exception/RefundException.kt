package com.hoppingmall.mall.refund.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.refund.exception.code.RefundErrorCode

open class RefundException(
    errorCode: RefundErrorCode
) : BusinessException(errorCode)
