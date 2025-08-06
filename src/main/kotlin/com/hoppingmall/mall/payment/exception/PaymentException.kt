package com.hoppingmall.mall.payment.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.payment.exception.code.PaymentErrorCode

open class PaymentException(
    errorCode: PaymentErrorCode
) : BusinessException(errorCode) 