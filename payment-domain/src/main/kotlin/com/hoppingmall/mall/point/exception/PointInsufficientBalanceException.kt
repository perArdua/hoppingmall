package com.hoppingmall.mall.point.exception

import com.hoppingmall.mall.point.exception.code.PointErrorCode

class PointInsufficientBalanceException : PointException(PointErrorCode.POINT_INSUFFICIENT_BALANCE) 