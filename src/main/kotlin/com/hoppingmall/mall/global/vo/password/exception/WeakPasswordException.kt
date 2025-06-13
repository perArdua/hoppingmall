package com.hoppingmall.mall.global.vo.password.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException

class WeakPasswordException : BusinessException(PasswordErrorCode.WEAK_PASSWORD)