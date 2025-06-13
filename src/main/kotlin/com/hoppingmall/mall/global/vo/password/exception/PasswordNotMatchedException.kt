package com.hoppingmall.mall.global.vo.password.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException

class PasswordNotMatchedException : BusinessException(PasswordErrorCode.PASSWORD_NOT_MATCHED)