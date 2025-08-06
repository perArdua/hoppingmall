package com.hoppingmall.mall.point.exception

import com.hoppingmall.mall.global.exception.BusinessException
import com.hoppingmall.mall.point.exception.code.PointErrorCode

open class PointException(errorCode: PointErrorCode) : BusinessException(errorCode)