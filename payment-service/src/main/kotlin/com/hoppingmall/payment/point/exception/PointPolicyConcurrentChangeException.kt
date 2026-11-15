package com.hoppingmall.payment.point.exception

import com.hoppingmall.payment.point.exception.code.PointErrorCode

class PointPolicyConcurrentChangeException : PointException(PointErrorCode.POINT_POLICY_CONCURRENT_CHANGE)
