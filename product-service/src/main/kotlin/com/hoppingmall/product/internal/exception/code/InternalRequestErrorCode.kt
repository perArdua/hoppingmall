package com.hoppingmall.product.internal.exception.code

import com.hoppingmall.common.ErrorCode
import org.springframework.http.HttpStatus

enum class InternalRequestErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    INVALID_REQUEST_KEY("IRK001", "요청 키는 비어 있지 않은 64자 이하 문자열이어야 합니다.", HttpStatus.BAD_REQUEST)
}
