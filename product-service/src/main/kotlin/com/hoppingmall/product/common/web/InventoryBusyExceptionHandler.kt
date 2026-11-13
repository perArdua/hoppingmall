package com.hoppingmall.product.common.web

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.product.inventory.exception.InventoryBusyException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class InventoryBusyExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InventoryBusyException::class)
    fun handleInventoryBusy(ex: InventoryBusyException): ResponseEntity<ApiResponse<Unit>> {
        log.debug("InventoryBusy: code={}", ex.errorCode.code)
        return ResponseEntity.status(ex.errorCode.status)
            .body(ApiResponse.failure(ex.errorCode))
    }
}
