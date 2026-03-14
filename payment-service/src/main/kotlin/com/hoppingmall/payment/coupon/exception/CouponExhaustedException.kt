package com.hoppingmall.payment.coupon.exception

import com.hoppingmall.payment.coupon.exception.code.CouponErrorCode

class CouponExhaustedException : CouponException(CouponErrorCode.COUPON_EXHAUSTED)
