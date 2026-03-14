package com.hoppingmall.user.common

open class BusinessException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message)
