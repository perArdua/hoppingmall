package com.hoppingmall.payment.coupon.exception

import com.hoppingmall.payment.coupon.exception.code.CouponErrorCode

class CouponExpiredException : CouponException(CouponErrorCode.COUPON_EXPIRED)
