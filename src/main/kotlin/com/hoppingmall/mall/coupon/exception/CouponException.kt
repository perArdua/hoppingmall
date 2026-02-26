package com.hoppingmall.mall.coupon.exception

import com.hoppingmall.mall.coupon.exception.code.CouponErrorCode
import com.hoppingmall.mall.global.common.error.exception.BusinessException

open class CouponException(
    errorCode: CouponErrorCode
) : BusinessException(errorCode)
