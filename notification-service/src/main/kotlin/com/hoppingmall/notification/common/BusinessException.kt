package com.hoppingmall.notification.common

open class BusinessException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message)
