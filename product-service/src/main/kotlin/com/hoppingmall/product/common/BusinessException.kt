package com.hoppingmall.product.common

open class BusinessException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message)
