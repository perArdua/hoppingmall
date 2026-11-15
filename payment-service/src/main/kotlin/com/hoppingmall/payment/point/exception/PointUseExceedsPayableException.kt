package com.hoppingmall.payment.point.exception

import com.hoppingmall.payment.point.exception.code.PointErrorCode

class PointUseExceedsPayableException : PointException(PointErrorCode.POINT_USE_EXCEEDS_PAYABLE)
