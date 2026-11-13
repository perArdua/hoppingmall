package com.hoppingmall.payment.payment.exception.code

import com.hoppingmall.common.ErrorCode
import org.springframework.http.HttpStatus

enum class PaymentErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    PAYMENT_NOT_FOUND("PAY001", "결제 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PAYMENT_FAILED("PAY002", "결제에 실패했습니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_INVALID_AMOUNT("PAY003", "유효하지 않은 결제 금액입니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_INVALID_ORDER("PAY004", "유효하지 않은 주문 정보입니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_ACCESS_DENIED("PAY005", "결제에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    PAYMENT_INVALID_STATE("PAY006", "결제 상태가 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_ORDER_NOT_FOUND("PAY007", "주문을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PAYMENT_ORDER_UNAVAILABLE("PAY008", "주문을 확인할 수 없어 결제를 받을 수 없습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.SERVICE_UNAVAILABLE),
    PAYMENT_ORDER_NOT_PAYABLE("PAY009", "결제를 받을 수 없는 주문 상태입니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_DUPLICATE("PAY010", "이미 결제가 있는 주문입니다.", HttpStatus.CONFLICT),
    PAYMENT_POINT_USE_NOT_ALLOWED("PAY011", "결제에 포인트를 사용할 수 없습니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_DIRECT_CANCEL_NOT_ALLOWED("PAY012", "결제 대기 중인 주문만 결제를 직접 취소할 수 있습니다. 주문 취소를 이용해 주세요.", HttpStatus.BAD_REQUEST)
}
