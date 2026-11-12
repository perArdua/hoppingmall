package com.hoppingmall.product.common.web.exception.code

import com.hoppingmall.common.ErrorCode
import org.springframework.http.HttpStatus

enum class RequestErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    RESOURCE_NOT_FOUND("REQ001", "요청한 경로를 찾을 수 없습니다.", HttpStatus.NOT_FOUND)
}
