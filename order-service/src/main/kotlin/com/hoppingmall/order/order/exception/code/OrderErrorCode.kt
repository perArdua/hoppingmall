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
    ORDER_OUT_OF_STOCK("ORD007", "재고가 부족합니다.", HttpStatus.BAD_REQUEST),
    ORDER_BUSY("ORD008", "주문이 몰려 처리할 수 없습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    ORDER_RESERVATION_FENCED("ORD009", "재고 예약이 만료 처리되었습니다. 다시 주문해주세요.", HttpStatus.CONFLICT),
    ORDER_PRODUCT_UNAVAILABLE("ORD010", "상품 정보를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.", HttpStatus.SERVICE_UNAVAILABLE),
    ORDER_TOTAL_AMOUNT_EXCEEDED("ORD011", "주문 합계는 99,999,999.99원을 넘을 수 없습니다.", HttpStatus.BAD_REQUEST),
    ORDER_PARTIALLY_REFUNDED("ORD012", "부분 환불된 주문은 취소할 수 없습니다. 남은 수량은 환불을 요청해주세요.", HttpStatus.BAD_REQUEST),
}
