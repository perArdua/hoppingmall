package com.hoppingmall.order.order.exception

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.order.order.dto.response.OrderPriceChangedResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class OrderPriceChangedExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(OrderPriceChangedException::class)
    fun handleOrderPriceChanged(ex: OrderPriceChangedException): ResponseEntity<ApiResponse<OrderPriceChangedResponse>> {
        log.info("주문 가격 바뀜: code={}, cartItemIds={}", ex.errorCode.code, ex.changes.map { it.cartItemId })
        return ResponseEntity.status(ex.errorCode.status)
            .body(
                ApiResponse(
                    code = ex.errorCode.code,
                    message = ex.errorCode.message,
                    data = OrderPriceChangedResponse(ex.changes)
                )
            )
    }
}
