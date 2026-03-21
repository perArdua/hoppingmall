package com.hoppingmall.product.review.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.product.review.exception.code.ReviewErrorCode

open class ReviewException(
    errorCode: ReviewErrorCode
) : BusinessException(errorCode)
