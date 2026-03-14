package com.hoppingmall.payment.point.exception

import com.hoppingmall.payment.common.BusinessException
import com.hoppingmall.payment.point.exception.code.PointErrorCode

open class PointException(errorCode: PointErrorCode) : BusinessException(errorCode)
