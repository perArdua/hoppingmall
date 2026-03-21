package com.hoppingmall.user.exception.membership

import com.hoppingmall.common.ErrorCode
import org.springframework.http.HttpStatus

enum class MembershipErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    MEMBERSHIP_NOT_FOUND(
        code = "MEMBERSHIP_NOT_FOUND",
        message = "멤버십 정보를 찾을 수 없습니다",
        status = HttpStatus.NOT_FOUND
    ),
    MEMBERSHIP_ALREADY_EXISTS(
        code = "MEMBERSHIP_ALREADY_EXISTS",
        message = "이미 멤버십이 존재합니다",
        status = HttpStatus.CONFLICT
    )
}
