package com.hoppingmall.user.exception.user

import java.time.Duration

class LoginAttemptsExceededException(
    val retryAfter: Duration
) : UserException(UserErrorCode.LOGIN_ATTEMPTS_EXCEEDED)
