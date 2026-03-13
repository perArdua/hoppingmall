package com.hoppingmall.mall.coupon.exception

import com.hoppingmall.mall.coupon.exception.code.CouponErrorCode

class CouponNotAvailableException : CouponException(CouponErrorCode.COUPON_NOT_AVAILABLE)
