package com.hoppingmall.mall.point.exception

import com.hoppingmall.mall.point.exception.code.PointErrorCode

class PointPolicyNotFoundException : PointException(PointErrorCode.POINT_POLICY_NOT_FOUND) 