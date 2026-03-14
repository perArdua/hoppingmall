package com.hoppingmall.product.review.exception

import com.hoppingmall.product.review.exception.code.ReviewErrorCode

class ReviewNotFoundException : ReviewException(ReviewErrorCode.REVIEW_NOT_FOUND)
