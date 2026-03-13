package com.hoppingmall.mall.coupon.exception

import com.hoppingmall.mall.coupon.exception.code.CouponErrorCode

class CouponExpiredException : CouponException(CouponErrorCode.COUPON_EXPIRED)
