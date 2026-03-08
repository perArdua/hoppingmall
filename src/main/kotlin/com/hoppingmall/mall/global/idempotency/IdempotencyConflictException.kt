package com.hoppingmall.mall.global.idempotency

import com.hoppingmall.mall.global.common.error.exception.BusinessException

class IdempotencyConflictException : BusinessException(IdempotencyErrorCode.IDEMPOTENCY_KEY_CONFLICT)
