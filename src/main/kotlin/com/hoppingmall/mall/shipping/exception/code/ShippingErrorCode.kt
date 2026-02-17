package com.hoppingmall.mall.shipping.exception.code

import com.hoppingmall.mall.global.common.error.code.ErrorCode
import org.springframework.http.HttpStatus

enum class ShippingErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    SHIPPING_NOT_FOUND("SHP001", "배송 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    SHIPPING_ALREADY_EXISTS("SHP002", "이미 배송 정보가 존재합니다.", HttpStatus.CONFLICT),
    SHIPPING_INVALID_STATUS("SHP003", "유효하지 않은 배송 상태 변경입니다.", HttpStatus.BAD_REQUEST),
}
