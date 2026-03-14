package com.hoppingmall.mall.global.idempotency

import com.hoppingmall.mall.global.common.error.code.ErrorCode
import org.springframework.http.HttpStatus

enum class IdempotencyErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    IDEMPOTENCY_KEY_MISSING("IDEM001", "Idempotency-Key 헤더가 필요합니다.", HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_KEY_CONFLICT("IDEM002", "동일한 요청이 처리 중입니다. 잠시 후 다시 시도해 주세요.", HttpStatus.CONFLICT)
}
