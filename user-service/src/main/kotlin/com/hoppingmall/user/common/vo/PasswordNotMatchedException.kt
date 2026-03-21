package com.hoppingmall.user.common.vo

import com.hoppingmall.common.BusinessException

class PasswordNotMatchedException : BusinessException(PasswordErrorCode.PASSWORD_NOT_MATCHED)
