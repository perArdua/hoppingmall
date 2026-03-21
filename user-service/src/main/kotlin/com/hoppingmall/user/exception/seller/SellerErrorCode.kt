package com.hoppingmall.user.exception.seller

import com.hoppingmall.common.ErrorCode
import org.springframework.http.HttpStatus

enum class SellerErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    ALREADY_APPLIED("S001", "이미 판매자 신청이 완료된 사용자입니다.", HttpStatus.CONFLICT),
    BUSINESS_NUMBER_DUPLICATED("S002", "이미 등록된 사업자등록번호입니다.", HttpStatus.CONFLICT),
    INVALID_APPROVAL_STATUS("S003", "유효하지 않은 판매자 승인 상태입니다.", HttpStatus.BAD_REQUEST),
    SELLER_NOT_FOUND("S004", "존재하지 않는 판매자입니다.", HttpStatus.NOT_FOUND),
    SELLER_INVALID_APPROVAL_COMMAND("S005", "지원하지 않는 판매자 승인 상태입니다.", HttpStatus.BAD_REQUEST)
}
