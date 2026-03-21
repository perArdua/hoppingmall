package com.hoppingmall.payment.internal

import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.ErrorCode
import org.springframework.http.HttpStatus

class DistributedLockException : BusinessException(DistributedLockErrorCode.LOCK_ACQUISITION_FAILED)

private enum class DistributedLockErrorCode(
    override val code: String,
    override val message: String,
    override val status: HttpStatus
) : ErrorCode {
    LOCK_ACQUISITION_FAILED("LOCK_ACQUISITION_FAILED", "락 획득에 실패했습니다", HttpStatus.CONFLICT)
}
