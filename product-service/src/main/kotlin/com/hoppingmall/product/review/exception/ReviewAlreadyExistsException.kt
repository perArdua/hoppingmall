package com.hoppingmall.product.review.exception

import com.hoppingmall.product.review.exception.code.ReviewErrorCode

class ReviewAlreadyExistsException : ReviewException(ReviewErrorCode.REVIEW_ALREADY_EXISTS)
