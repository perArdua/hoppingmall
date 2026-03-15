package com.hoppingmall.settlement.common

open class BusinessException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message)
