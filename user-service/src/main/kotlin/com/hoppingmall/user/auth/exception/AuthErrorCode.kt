package com.hoppingmall.user.auth.exception

import com.hoppingmall.user.common.ErrorCode
import org.springframework.http.HttpStatus

enum class AuthErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {

    REFRESH_TOKEN_NOT_FOUND("A001", "리프레시 토큰이 존재하지 않습니다.", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_MISMATCH("A002", "리프레시 토큰이 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("A003", "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED)
}
