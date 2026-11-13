package com.hoppingmall.order.cartItem.exception.code

import com.hoppingmall.common.ErrorCode
import org.springframework.http.HttpStatus

enum class CartItemErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    CART_ITEM_NOT_FOUND("CART001", "장바구니 아이템을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CART_ITEM_ACCESS_DENIED("CART002", "본인의 장바구니만 수정할 수 있습니다.", HttpStatus.FORBIDDEN),
    CART_ITEM_PRODUCT_NOT_FOUND("CART003", "상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CART_ITEM_CONCURRENT_ADD("CART004", "같은 상품을 동시에 담는 요청이 있어 처리하지 못했습니다. 다시 시도해 주세요.", HttpStatus.CONFLICT),
    CART_ITEM_INVALID_QUANTITY("CART005", "장바구니 수량은 1개 이상 999,999,999개 이하여야 합니다.", HttpStatus.BAD_REQUEST),
    CART_ITEM_PRODUCT_UNAVAILABLE("CART006", "상품 정보를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.SERVICE_UNAVAILABLE),
}
