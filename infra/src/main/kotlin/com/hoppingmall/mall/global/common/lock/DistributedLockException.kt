package com.hoppingmall.mall.global.common.lock

import com.hoppingmall.mall.global.common.error.code.CommonErrorCode
import com.hoppingmall.mall.global.common.error.exception.BusinessException

class DistributedLockException : BusinessException(CommonErrorCode.LOCK_ACQUISITION_FAILED)
