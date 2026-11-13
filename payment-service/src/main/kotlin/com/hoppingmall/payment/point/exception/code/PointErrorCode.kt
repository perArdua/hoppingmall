package com.hoppingmall.payment.point.exception.code

import com.hoppingmall.common.ErrorCode
import org.springframework.http.HttpStatus

enum class PointErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    POINT_INSUFFICIENT_BALANCE(
        code = "POINT_INSUFFICIENT_BALANCE",
        message = "포인트 잔액이 부족합니다",
        status = HttpStatus.BAD_REQUEST
    ),
    POINT_POLICY_NOT_FOUND(
        code = "POINT_POLICY_NOT_FOUND",
        message = "포인트 정책을 찾을 수 없습니다",
        status = HttpStatus.NOT_FOUND
    )
}
