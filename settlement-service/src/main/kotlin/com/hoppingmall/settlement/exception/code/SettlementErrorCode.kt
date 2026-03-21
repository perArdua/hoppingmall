package com.hoppingmall.settlement.exception.code

import com.hoppingmall.common.ErrorCode
import org.springframework.http.HttpStatus

enum class SettlementErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    SETTLEMENT_NOT_FOUND("STL001", "정산 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    SETTLEMENT_INVALID_STATUS("STL002", "정산 상태가 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    SETTLEMENT_ALREADY_EXISTS("STL003", "해당 기간의 정산이 이미 존재합니다.", HttpStatus.CONFLICT),
    SETTLEMENT_INVALID_PERIOD("STL004", "정산 기간이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    SETTLEMENT_ACCESS_DENIED("STL005", "정산에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    SETTLEMENT_NO_SALES_DATA("STL006", "해당 기간에 정산할 매출 데이터가 없습니다.", HttpStatus.BAD_REQUEST),
    SETTLEMENT_SELLER_NOT_FOUND("STL007", "판매자 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    SERVICE_COMMUNICATION_ERROR("STL008", "서비스 간 통신 오류가 발생했습니다.", HttpStatus.SERVICE_UNAVAILABLE)
}
