package com.hoppingmall.mall.coupon.exception

import com.hoppingmall.mall.coupon.exception.code.CouponErrorCode

class CouponExhaustedException : CouponException(CouponErrorCode.COUPON_EXHAUSTED)
