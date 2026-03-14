package com.hoppingmall.payment.coupon.exception

import com.hoppingmall.payment.coupon.exception.code.CouponErrorCode

class CouponAlreadyIssuedException : CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED)
