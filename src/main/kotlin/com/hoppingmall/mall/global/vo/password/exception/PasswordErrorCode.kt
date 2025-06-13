package com.hoppingmall.mall.global.vo.password.exception

import com.hoppingmall.mall.global.common.error.code.ErrorCode
import org.springframework.http.HttpStatus

enum class PasswordErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    WEAK_PASSWORD("P001", "비밀번호가 정책을 만족하지 않습니다.", HttpStatus.BAD_REQUEST),
    PASSWORD_NOT_MATCHED("P002", "비밀번호가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED)
}
