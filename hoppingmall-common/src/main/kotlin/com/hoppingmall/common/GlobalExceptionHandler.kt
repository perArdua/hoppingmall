package com.hoppingmall.common

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(ex: BusinessException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("BusinessException: code={}, message={}", ex.errorCode.code, ex.errorCode.message)
        val status = ex.errorCode.status
        return ResponseEntity.status(status)
            .body(ApiResponse.failure(ex.errorCode))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {
        val message = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: CommonErrorCode.INVALID_INPUT.message
        log.warn("ValidationException: {}", message)
        return ResponseEntity.badRequest().body(ApiResponse.failure(CommonErrorCode.INVALID_INPUT))
    }

    @ExceptionHandler(
        MissingServletRequestParameterException::class,
        MissingRequestHeaderException::class,
        MethodArgumentTypeMismatchException::class,
        HttpMessageNotReadableException::class
    )
    fun handleBadRequest(ex: Exception): ResponseEntity<ApiResponse<Unit>> {
        log.warn("BadRequest: {}", ex.message)
        return ResponseEntity.badRequest().body(ApiResponse.failure(CommonErrorCode.INVALID_INPUT))
    }

    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun handleNotFound(ex: Exception): ResponseEntity<ApiResponse<Unit>> {
        log.debug("NotFound: {}", ex.message)
        return ResponseEntity.status(CommonErrorCode.RESOURCE_NOT_FOUND.status)
            .body(ApiResponse.failure(CommonErrorCode.RESOURCE_NOT_FOUND))
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(ex: HttpRequestMethodNotSupportedException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("MethodNotAllowed: {}", ex.message)
        return ResponseEntity.status(CommonErrorCode.METHOD_NOT_ALLOWED.status)
            .headers(ex.headers)
            .body(ApiResponse.failure(CommonErrorCode.METHOD_NOT_ALLOWED))
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedMediaType(ex: HttpMediaTypeNotSupportedException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("UnsupportedMediaType: {}", ex.message)
        return ResponseEntity.status(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.status)
            .headers(ex.headers)
            .body(ApiResponse.failure(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ResponseEntity<ApiResponse<Unit>> {
        log.error("UnhandledException: {}", ex.message, ex)
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.status)
            .body(ApiResponse.failure(CommonErrorCode.INTERNAL_ERROR))
    }
}
