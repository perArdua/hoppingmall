package com.hoppingmall.order.refund.exception.code

import com.hoppingmall.common.ErrorCode
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
    REFUND_PAYMENT_NOT_FOUND("RFD009", "결제 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    REFUND_DUPLICATE_ITEM("RFD010", "같은 주문 아이템이 환불 요청에 두 번 이상 들어 있습니다.", HttpStatus.BAD_REQUEST),
    REFUND_WINDOW_EXPIRED("RFD011", "단순 변심 환불 요청 기한이 지났습니다.", HttpStatus.BAD_REQUEST),
    REFUND_SELLER_FAULT_WINDOW_EXPIRED("RFD012", "상품 하자·오배송·설명과 다른 상품의 환불 요청 기한이 지났습니다.", HttpStatus.BAD_REQUEST),
    REFUND_REASON_NOT_ACCEPTED("RFD013", "새 환불 요청에 쓸 수 없는 사유입니다.", HttpStatus.BAD_REQUEST),
    REFUND_NOT_COMPLETED("RFD014", "완료된 전체 환불이 없는 주문은 환불 완료로 바꿀 수 없습니다.", HttpStatus.CONFLICT),
    REFUND_RETURN_SHIPPING_FEE_EXCEEDED("RFD015", "반품 배송비가 환불할 상품 금액보다 큽니다.", HttpStatus.BAD_REQUEST),
    REFUND_WITHDRAWAL_RESTRICTED("RFD016", "청약철회가 제한된 상품은 단순 변심으로 환불할 수 없습니다.", HttpStatus.BAD_REQUEST),
}
