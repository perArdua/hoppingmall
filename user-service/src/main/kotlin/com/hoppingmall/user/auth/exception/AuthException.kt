package com.hoppingmall.user.auth.exception

import com.hoppingmall.user.common.BusinessException

open class AuthException(errorCode: AuthErrorCode) : BusinessException(errorCode)
