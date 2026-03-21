package com.hoppingmall.order.order.exception.code

import com.hoppingmall.common.ErrorCode
import org.springframework.http.HttpStatus

enum class OrderErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    ORDER_NOT_FOUND("ORD001", "주문을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    ORDER_ACCESS_DENIED("ORD002", "본인의 주문만 조회할 수 있습니다.", HttpStatus.FORBIDDEN),
    ORDER_INVALID_STATUS("ORD003", "유효하지 않은 주문 상태 변경입니다.", HttpStatus.BAD_REQUEST),
    ORDER_EMPTY_ITEMS("ORD004", "주문 항목이 비어있습니다.", HttpStatus.BAD_REQUEST),
    ORDER_PRODUCT_NOT_FOUND("ORD005", "상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
}
