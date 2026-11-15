package com.hoppingmall.idempotency

import com.hoppingmall.common.BusinessException

class IdempotencyKeyReusedException : BusinessException(IdempotencyErrorCode.IDEMPOTENCY_KEY_REUSED)
