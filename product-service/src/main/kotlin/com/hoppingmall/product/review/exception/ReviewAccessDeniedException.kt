package com.hoppingmall.product.review.exception

import com.hoppingmall.product.review.exception.code.ReviewErrorCode

class ReviewAccessDeniedException : ReviewException(ReviewErrorCode.REVIEW_ACCESS_DENIED)
