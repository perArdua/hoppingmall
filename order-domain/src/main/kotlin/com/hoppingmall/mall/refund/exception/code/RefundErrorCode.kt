package com.hoppingmall.mall.refund.exception.code

import com.hoppingmall.mall.global.common.error.code.ErrorCode
import org.springframework.http.HttpStatus

enum class RefundErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    REFUND_NOT_FOUND("RFD001", "환불 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    REFUND_ACCESS_DENIED("RFD002", "환불에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    REFUND_INVALID_STATUS("RFD003", "환불 상태가 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    REFUND_ALREADY_EXISTS("RFD004", "이미 진행 중인 환불 요청이 있습니다.", HttpStatus.CONFLICT),
    REFUND_INVALID_ORDER_STATUS("RFD005", "현재 주문 상태에서는 환불을 요청할 수 없습니다.", HttpStatus.BAD_REQUEST),
    REFUND_INVALID_PAYMENT_STATUS("RFD006", "결제 상태가 유효하지 않아 환불을 요청할 수 없습니다.", HttpStatus.BAD_REQUEST),
    REFUND_INVALID_ITEM("RFD007", "환불 요청 아이템이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    REFUND_QUANTITY_EXCEEDED("RFD008", "환불 가능한 수량을 초과했습니다.", HttpStatus.BAD_REQUEST),
}
