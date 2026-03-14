package com.hoppingmall.mall.global.auth.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException

open class AuthException(errorCode: AuthErrorCode) : BusinessException(errorCode)
