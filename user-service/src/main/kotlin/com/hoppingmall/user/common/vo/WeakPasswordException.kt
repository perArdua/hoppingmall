package com.hoppingmall.user.common.vo

import com.hoppingmall.common.BusinessException

class WeakPasswordException : BusinessException(PasswordErrorCode.WEAK_PASSWORD)
