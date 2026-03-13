package com.hoppingmall.mall.coupon.exception

import com.hoppingmall.mall.coupon.exception.code.CouponErrorCode

class CouponAlreadyIssuedException : CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED)
