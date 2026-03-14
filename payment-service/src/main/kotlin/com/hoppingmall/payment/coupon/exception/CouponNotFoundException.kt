package com.hoppingmall.payment.coupon.exception

import com.hoppingmall.payment.coupon.exception.code.CouponErrorCode

class CouponNotFoundException : CouponException(CouponErrorCode.COUPON_NOT_FOUND)
