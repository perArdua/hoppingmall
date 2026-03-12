package com.hoppingmall.mall.product.exception.code


import com.hoppingmall.mall.global.common.error.code.ErrorCode
import org.springframework.http.HttpStatus

enum class ProductErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    PRODUCT_NOT_FOUND("P001", "상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PRODUCT_ALREADY_EXISTS("P002", "이미 존재하는 상품입니다.", HttpStatus.CONFLICT),
    PRODUCT_INVALID_STATUS("P003", "유효하지 않은 상품 상태입니다.", HttpStatus.BAD_REQUEST),
    PRODUCT_ACCESS_DENIED("P004", "상품에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    PRODUCT_STATISTICS_NOT_FOUND("P005", "상품 통계를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    BULK_IMPORT_INVALID_CSV("P006", "CSV 파일 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    BULK_IMPORT_JOB_NOT_FOUND("P007", "대량 등록 작업을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    BULK_IMPORT_ACCESS_DENIED("P008", "대량 등록 작업에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN)
} 