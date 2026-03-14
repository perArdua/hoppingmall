package com.hoppingmall.payment.coupon.exception.code

import com.hoppingmall.payment.common.ErrorCode
import org.springframework.http.HttpStatus

enum class CouponErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    COUPON_NOT_FOUND("CPN001", "쿠폰을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    COUPON_EXHAUSTED("CPN002", "쿠폰 발급 수량이 소진되었습니다.", HttpStatus.BAD_REQUEST),
    COUPON_EXPIRED("CPN003", "쿠폰 유효기간이 만료되었습니다.", HttpStatus.BAD_REQUEST),
    COUPON_ALREADY_ISSUED("CPN004", "이미 발급받은 쿠폰입니다.", HttpStatus.CONFLICT),
    COUPON_NOT_AVAILABLE("CPN005", "사용할 수 없는 쿠폰입니다.", HttpStatus.BAD_REQUEST),
    COUPON_MIN_AMOUNT_NOT_MET("CPN006", "최소 주문 금액을 충족하지 않습니다.", HttpStatus.BAD_REQUEST)
}
