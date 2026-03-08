package com.hoppingmall.mall.global.idempotency

import com.hoppingmall.mall.global.common.error.exception.BusinessException

class IdempotencyKeyMissingException : BusinessException(IdempotencyErrorCode.IDEMPOTENCY_KEY_MISSING)
