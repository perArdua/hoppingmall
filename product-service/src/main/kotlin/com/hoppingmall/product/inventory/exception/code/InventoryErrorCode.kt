package com.hoppingmall.product.inventory.exception.code

import com.hoppingmall.common.ErrorCode
import org.springframework.http.HttpStatus

enum class InventoryErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    INVENTORY_NOT_FOUND("INV001", "재고 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVENTORY_INSUFFICIENT_STOCK("INV002", "재고가 부족합니다.", HttpStatus.BAD_REQUEST),
    INVENTORY_ALREADY_EXISTS("INV003", "해당 상품의 재고가 이미 존재합니다.", HttpStatus.CONFLICT),
    RESERVATION_CONFIRM_FAILED("INV004", "재고 예약 확정에 실패했습니다.", HttpStatus.CONFLICT),
    INVENTORY_BUSY("INV005", "재고 요청이 몰려 처리할 수 없습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
}
