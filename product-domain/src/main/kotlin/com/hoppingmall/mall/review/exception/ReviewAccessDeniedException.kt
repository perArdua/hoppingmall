package com.hoppingmall.mall.review.exception

import com.hoppingmall.mall.review.exception.code.ReviewErrorCode

class ReviewAccessDeniedException : ReviewException(ReviewErrorCode.REVIEW_ACCESS_DENIED)
