package com.hoppingmall.mall.global.common.error.exception

import com.hoppingmall.mall.global.common.error.code.ErrorCode

open class BusinessException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message)