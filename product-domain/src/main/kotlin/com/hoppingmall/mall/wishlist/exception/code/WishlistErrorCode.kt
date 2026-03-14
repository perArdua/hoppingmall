package com.hoppingmall.mall.wishlist.exception.code

import com.hoppingmall.mall.global.common.error.code.ErrorCode
import org.springframework.http.HttpStatus

enum class WishlistErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    WISHLIST_NOT_FOUND("WISH001", "찜 항목을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    WISHLIST_ALREADY_EXISTS("WISH002", "이미 찜한 상품입니다.", HttpStatus.CONFLICT),
}
