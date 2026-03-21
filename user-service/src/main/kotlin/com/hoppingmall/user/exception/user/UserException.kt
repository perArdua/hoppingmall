package com.hoppingmall.user.exception.user

import com.hoppingmall.common.BusinessException

open class UserException(
    errorCode: UserErrorCode
) : BusinessException(errorCode)
