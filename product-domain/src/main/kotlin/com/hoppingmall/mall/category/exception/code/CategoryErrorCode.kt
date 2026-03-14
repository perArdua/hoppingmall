package com.hoppingmall.mall.category.exception.code

import com.hoppingmall.mall.global.common.error.code.ErrorCode
import org.springframework.http.HttpStatus

enum class CategoryErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    CATEGORY_NOT_FOUND("CAT001", "카테고리를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CATEGORY_ALREADY_EXISTS("CAT002", "이미 존재하는 카테고리 이름입니다.", HttpStatus.CONFLICT),
    CATEGORY_HAS_CHILDREN("CAT003", "하위 카테고리가 존재하여 삭제할 수 없습니다.", HttpStatus.BAD_REQUEST),
    CATEGORY_CIRCULAR_REFERENCE("CAT004", "순환 참조가 발생하는 카테고리 구조입니다.", HttpStatus.BAD_REQUEST),
}
