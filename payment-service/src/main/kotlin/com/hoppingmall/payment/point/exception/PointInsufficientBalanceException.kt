package com.hoppingmall.payment.point.exception

import com.hoppingmall.payment.point.exception.code.PointErrorCode

class PointInsufficientBalanceException : PointException(PointErrorCode.POINT_INSUFFICIENT_BALANCE)
