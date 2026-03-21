package com.hoppingmall.payment.coupon.exception

import com.hoppingmall.payment.coupon.exception.code.CouponErrorCode
import com.hoppingmall.common.BusinessException

open class CouponException(
    errorCode: CouponErrorCode
) : BusinessException(errorCode)
