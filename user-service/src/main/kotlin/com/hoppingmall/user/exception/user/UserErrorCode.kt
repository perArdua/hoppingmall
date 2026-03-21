package com.hoppingmall.user.exception.user

import com.hoppingmall.common.ErrorCode
import org.springframework.http.HttpStatus

enum class UserErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    USER_ALREADY_EXISTS("U001", "이미 존재하는 이메일입니다.", HttpStatus.CONFLICT),
    LOGIN_FAILED("U002", "이메일 또는 비밀번호가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND("U003", "존재하지 않는 사용자입니다.", HttpStatus.NOT_FOUND)
}
