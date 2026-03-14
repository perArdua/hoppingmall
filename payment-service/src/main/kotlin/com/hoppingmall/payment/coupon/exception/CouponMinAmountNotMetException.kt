package com.hoppingmall.payment.coupon.exception

import com.hoppingmall.payment.coupon.exception.code.CouponErrorCode

class CouponMinAmountNotMetException : CouponException(CouponErrorCode.COUPON_MIN_AMOUNT_NOT_MET)
