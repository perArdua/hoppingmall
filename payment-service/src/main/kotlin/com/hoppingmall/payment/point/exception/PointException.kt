package com.hoppingmall.payment.point.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.payment.point.exception.code.PointErrorCode

open class PointException(errorCode: PointErrorCode) : BusinessException(errorCode)
