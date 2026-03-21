package com.hoppingmall.product.review.exception.code

import com.hoppingmall.common.ErrorCode
import org.springframework.http.HttpStatus

enum class ReviewErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    REVIEW_NOT_FOUND("REV001", "리뷰를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    REVIEW_ALREADY_EXISTS("REV002", "이미 해당 주문 상품에 대한 리뷰가 존재합니다.", HttpStatus.CONFLICT),
    REVIEW_ORDER_NOT_DELIVERED("REV003", "배송 완료된 주문에만 리뷰를 작성할 수 있습니다.", HttpStatus.BAD_REQUEST),
    REVIEW_ACCESS_DENIED("REV004", "리뷰에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    REVIEW_INVALID_ORDER_ITEM("REV005", "유효하지 않은 주문 상품입니다.", HttpStatus.BAD_REQUEST),
}
