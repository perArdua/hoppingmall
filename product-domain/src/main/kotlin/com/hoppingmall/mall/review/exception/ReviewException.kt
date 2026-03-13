package com.hoppingmall.mall.review.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.review.exception.code.ReviewErrorCode

open class ReviewException(
    errorCode: ReviewErrorCode
) : BusinessException(errorCode)
