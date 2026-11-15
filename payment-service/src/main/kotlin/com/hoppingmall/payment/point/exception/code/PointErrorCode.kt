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
    ),
    POINT_POLICY_NAME_DUPLICATED(
        code = "POINT_POLICY_NAME_DUPLICATED",
        message = "이미 존재하는 정책 이름입니다",
        status = HttpStatus.CONFLICT
    ),
    POINT_POLICY_INVALID(
        code = "POINT_POLICY_INVALID",
        message = "적립률은 0보다 크고 최대 적립률 이하, 최소 사용 금액은 최대 사용 금액 이하여야 합니다",
        status = HttpStatus.BAD_REQUEST
    )
}
