package com.hoppingmall.payment.payment.exception.code

import com.hoppingmall.payment.common.ErrorCode
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
    PAYMENT_INVALID_STATE("PAY006", "결제 상태가 유효하지 않습니다.", HttpStatus.BAD_REQUEST)
}
