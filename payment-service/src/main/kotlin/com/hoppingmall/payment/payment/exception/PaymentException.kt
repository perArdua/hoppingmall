package com.hoppingmall.payment.payment.exception

import com.hoppingmall.payment.common.BusinessException
import com.hoppingmall.payment.payment.exception.code.PaymentErrorCode

open class PaymentException(
    errorCode: PaymentErrorCode
) : BusinessException(errorCode)
