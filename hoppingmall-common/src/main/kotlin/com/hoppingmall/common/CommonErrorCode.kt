package com.hoppingmall.common

import org.springframework.http.HttpStatus

enum class CommonErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    INVALID_INPUT("C001", "잘못된 입력입니다.", HttpStatus.BAD_REQUEST),
    INVALID_STATE("C002", "지금 상태에서는 처리할 수 없는 요청입니다.", HttpStatus.CONFLICT),
    DUPLICATE_RESOURCE("C003", "이미 같은 데이터가 있습니다.", HttpStatus.CONFLICT),
    UNAUTHORIZED("A001", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("A002", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    INTERNAL_ERROR("S001", "서버 내부 오류입니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    LOCK_ACQUISITION_FAILED("S002", "요청 처리 중입니다. 잠시 후 다시 시도해 주세요.", HttpStatus.CONFLICT),
    RESOURCE_NOT_FOUND("REQ001", "요청한 경로를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("REQ002", "지원하지 않는 요청 메서드입니다.", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE("REQ003", "지원하지 않는 Content-Type입니다.", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    PAYLOAD_TOO_LARGE("REQ004", "업로드 크기 한도를 넘었습니다.", HttpStatus.PAYLOAD_TOO_LARGE)
}
