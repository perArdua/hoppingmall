package com.hoppingmall.global.common.error.exception

import com.hoppingmall.global.common.error.code.ErrorCode

open class BusinessException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message)