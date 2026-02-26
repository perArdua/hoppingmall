package com.hoppingmall.mall.review.exception

import com.hoppingmall.mall.review.exception.code.ReviewErrorCode

class ReviewNotFoundException : ReviewException(ReviewErrorCode.REVIEW_NOT_FOUND)
