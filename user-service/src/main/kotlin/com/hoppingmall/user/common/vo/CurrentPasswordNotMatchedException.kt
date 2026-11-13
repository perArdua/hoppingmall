package com.hoppingmall.user.common.vo

import com.hoppingmall.common.BusinessException

class CurrentPasswordNotMatchedException : BusinessException(PasswordErrorCode.CURRENT_PASSWORD_NOT_MATCHED)
