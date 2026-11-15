package com.hoppingmall.product.review.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.product.review.exception.code.ReviewErrorCode

class OrderQueryUnavailableException(cause: Throwable? = null) : BusinessException(ReviewErrorCode.ORDER_QUERY_UNAVAILABLE) {
    init {
        cause?.let { initCause(it) }
    }
}
