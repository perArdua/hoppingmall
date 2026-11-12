package com.hoppingmall.product.common.web

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.CommonErrorCode
import com.hoppingmall.product.common.web.exception.code.RequestErrorCode
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestErrorExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(ex: NoResourceFoundException): ResponseEntity<ApiResponse<Unit>> {
        log.debug("NoResourceFound: {} {}", ex.httpMethod, ex.resourcePath)
        return ResponseEntity.status(RequestErrorCode.RESOURCE_NOT_FOUND.status)
            .body(ApiResponse.failure(RequestErrorCode.RESOURCE_NOT_FOUND))
    }

    @ExceptionHandler(MissingServletRequestParameterException::class, HttpMessageNotReadableException::class)
    fun handleUnreadableRequest(ex: Exception): ResponseEntity<ApiResponse<Unit>> {
        log.warn("UnreadableRequest: {}", ex.message)
        return ResponseEntity.badRequest().body(ApiResponse.failure(CommonErrorCode.INVALID_INPUT))
    }
}
