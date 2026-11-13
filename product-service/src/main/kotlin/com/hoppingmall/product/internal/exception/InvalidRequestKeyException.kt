package com.hoppingmall.product.internal.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.product.internal.exception.code.InternalRequestErrorCode

class InvalidRequestKeyException : BusinessException(InternalRequestErrorCode.INVALID_REQUEST_KEY)
