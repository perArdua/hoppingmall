package com.hoppingmall.global.common.error.handler

import com.hoppingmall.global.common.error.exception.BusinessException
import com.hoppingmall.global.common.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(ex: BusinessException): ResponseEntity<ApiResponse<Unit>> {
        val status = ex.errorCode.status
        val response = ApiResponse.failure<Unit>(ex.errorCode.message)
        return ResponseEntity.status(status).body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ResponseEntity<ApiResponse<Unit>> {
        val response = ApiResponse.failure<Unit>("알 수 없는 오류가 발생했습니다.")
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }
}