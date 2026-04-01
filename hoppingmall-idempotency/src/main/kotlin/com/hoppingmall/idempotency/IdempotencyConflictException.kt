package com.hoppingmall.idempotency

import com.hoppingmall.common.BusinessException

class IdempotencyConflictException : BusinessException(IdempotencyErrorCode.IDEMPOTENCY_KEY_CONFLICT)
