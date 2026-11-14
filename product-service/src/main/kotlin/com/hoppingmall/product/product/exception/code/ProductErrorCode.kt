package com.hoppingmall.product.product.exception.code


import com.hoppingmall.common.ErrorCode
import org.springframework.http.HttpStatus

enum class ProductErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    PRODUCT_NOT_FOUND("P001", "상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PRODUCT_ACCESS_DENIED("P004", "상품에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    PRODUCT_STATISTICS_NOT_FOUND("P005", "상품 통계를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    BULK_IMPORT_INVALID_CSV("P006", "CSV 파일 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    BULK_IMPORT_JOB_NOT_FOUND("P007", "대량 등록 작업을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    BULK_IMPORT_ACCESS_DENIED("P008", "대량 등록 작업에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    SELLER_STATISTICS_ACCESS_DENIED("P009", "해당 상품의 통계에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    PRODUCT_SELLER_REQUIRED("P010", "관리자가 상품을 만들 때는 판매자 ID가 필수입니다.", HttpStatus.BAD_REQUEST)
} 