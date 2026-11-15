package com.hoppingmall.order.shipping.exception.code

import com.hoppingmall.common.ErrorCode
import org.springframework.http.HttpStatus

enum class ShippingErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    SHIPPING_NOT_FOUND("SHP001", "배송 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    SHIPPING_ALREADY_EXISTS("SHP002", "이미 배송 정보가 존재합니다.", HttpStatus.CONFLICT),
    SHIPPING_INVALID_STATUS("SHP003", "유효하지 않은 배송 상태 변경입니다.", HttpStatus.BAD_REQUEST),
    SHIPPING_ACCESS_DENIED("SHP004", "주문의 구매자·판매자·관리자만 배송 정보에 접근할 수 있습니다.", HttpStatus.FORBIDDEN),
    SHIPPING_RECIPIENT_NOT_ALLOWED("SHP005", "배송지가 있는 주문의 배송 등록에는 받는 사람 값을 보낼 수 없습니다.", HttpStatus.BAD_REQUEST),
    SHIPPING_SELF_ORDER("SHP006", "자기 주문의 배송은 등록하거나 바꿀 수 없습니다.", HttpStatus.FORBIDDEN),
}
