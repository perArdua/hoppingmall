package com.hoppingmall.payment.coupon.infrastructure

sealed class CouponRestoreResult {
    data object Restored : CouponRestoreResult()
    data object AlreadyRestored : CouponRestoreResult()
}
