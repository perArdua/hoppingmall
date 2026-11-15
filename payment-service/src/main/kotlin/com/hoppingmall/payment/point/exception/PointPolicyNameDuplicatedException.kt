package com.hoppingmall.payment.point.exception

import com.hoppingmall.payment.point.exception.code.PointErrorCode

class PointPolicyNameDuplicatedException : PointException(PointErrorCode.POINT_POLICY_NAME_DUPLICATED)
