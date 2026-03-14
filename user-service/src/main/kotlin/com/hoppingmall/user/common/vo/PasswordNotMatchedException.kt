package com.hoppingmall.user.common.vo

import com.hoppingmall.user.common.BusinessException

class PasswordNotMatchedException : BusinessException(PasswordErrorCode.PASSWORD_NOT_MATCHED)
