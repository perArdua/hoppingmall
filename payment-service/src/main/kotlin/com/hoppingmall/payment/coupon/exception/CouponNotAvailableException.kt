package com.hoppingmall.payment.coupon.exception

import com.hoppingmall.payment.coupon.exception.code.CouponErrorCode

class CouponNotAvailableException : CouponException(CouponErrorCode.COUPON_NOT_AVAILABLE)
