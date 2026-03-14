package com.hoppingmall.payment.common

open class BusinessException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message)
