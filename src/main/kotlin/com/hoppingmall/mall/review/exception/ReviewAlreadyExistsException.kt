package com.hoppingmall.mall.review.exception

import com.hoppingmall.mall.review.exception.code.ReviewErrorCode

class ReviewAlreadyExistsException : ReviewException(ReviewErrorCode.REVIEW_ALREADY_EXISTS)
