package com.hoppingmall.common

open class BusinessException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message)
