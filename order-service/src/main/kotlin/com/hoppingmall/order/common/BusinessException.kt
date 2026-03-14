package com.hoppingmall.order.common

open class BusinessException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message)
