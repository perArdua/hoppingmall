package com.hoppingmall.mall.user.exception.user

import com.hoppingmall.mall.global.common.error.exception.BusinessException

open class UserException(
    errorCode: UserErrorCode
) : BusinessException(errorCode)
