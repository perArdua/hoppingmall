package com.hoppingmall.mall.global.file.exception.code

import com.hoppingmall.mall.global.common.error.code.ErrorCode
import org.springframework.http.HttpStatus

enum class FileErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    EMPTY_FILE("F001", "업로드할 파일이 없습니다.", HttpStatus.BAD_REQUEST),
    FILE_SIZE_EXCEEDED("F002", "파일 크기는 5MB를 초과할 수 없습니다.", HttpStatus.BAD_REQUEST),
    INVALID_FILE_NAME("F003", "파일명이 없습니다.", HttpStatus.BAD_REQUEST),
    UNSUPPORTED_FILE_TYPE("F004", "허용되지 않는 파일 형식입니다.", HttpStatus.BAD_REQUEST),
    UNSUPPORTED_DOMAIN("F005", "허용되지 않는 도메인입니다.", HttpStatus.BAD_REQUEST),
    DIRECTORY_ACCESS_ERROR("F006", "업로드 디렉토리에 접근할 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_UPLOAD_ERROR("F007", "파일 업로드 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR)
} 