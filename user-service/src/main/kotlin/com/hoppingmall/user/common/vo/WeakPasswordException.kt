package com.hoppingmall.user.common.vo

import com.hoppingmall.user.common.BusinessException

class WeakPasswordException : BusinessException(PasswordErrorCode.WEAK_PASSWORD)
