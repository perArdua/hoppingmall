package com.hoppingmall.payment.point.exception

import com.hoppingmall.payment.point.exception.code.PointErrorCode

class PointPolicyNotFoundException : PointException(PointErrorCode.POINT_POLICY_NOT_FOUND)
