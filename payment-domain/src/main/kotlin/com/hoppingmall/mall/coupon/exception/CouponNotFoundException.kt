package com.hoppingmall.mall.coupon.exception

import com.hoppingmall.mall.coupon.exception.code.CouponErrorCode

class CouponNotFoundException : CouponException(CouponErrorCode.COUPON_NOT_FOUND)
