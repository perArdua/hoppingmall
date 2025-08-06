package com.hoppingmall.mall.point.exception.code

import com.hoppingmall.mall.global.exception.code.ErrorCode
import org.springframework.http.HttpStatus

enum class PointErrorCode(
    override val code: String,
    override val message: String,
    override val httpStatus: HttpStatus
) : ErrorCode {
    POINT_INSUFFICIENT_BALANCE(
        code = "POINT_INSUFFICIENT_BALANCE",
        message = "포인트 잔액이 부족합니다",
        httpStatus = HttpStatus.BAD_REQUEST
    ),
    POINT_INVALID_AMOUNT(
        code = "POINT_INVALID_AMOUNT",
        message = "유효하지 않은 포인트 금액입니다",
        httpStatus = HttpStatus.BAD_REQUEST
    ),
    POINT_NOT_FOUND(
        code = "POINT_NOT_FOUND",
        message = "포인트 정보를 찾을 수 없습니다",
        httpStatus = HttpStatus.NOT_FOUND
    ),
    POINT_POLICY_NOT_FOUND(
        code = "POINT_POLICY_NOT_FOUND",
        message = "포인트 정책을 찾을 수 없습니다",
        httpStatus = HttpStatus.NOT_FOUND
    )
} 