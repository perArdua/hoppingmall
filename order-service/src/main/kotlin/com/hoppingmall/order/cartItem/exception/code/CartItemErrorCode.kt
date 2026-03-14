package com.hoppingmall.order.cartItem.exception.code

import com.hoppingmall.order.common.ErrorCode
import org.springframework.http.HttpStatus

enum class CartItemErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    CART_ITEM_NOT_FOUND("C001", "장바구니 아이템을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CART_ITEM_ACCESS_DENIED("C002", "본인의 장바구니만 수정할 수 있습니다.", HttpStatus.FORBIDDEN),
    CART_ITEM_PRODUCT_NOT_FOUND("C003", "상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
}
