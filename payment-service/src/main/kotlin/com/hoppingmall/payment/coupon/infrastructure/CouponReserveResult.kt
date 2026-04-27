package com.hoppingmall.payment.coupon.infrastructure

sealed class CouponReserveResult {
    data object Success : CouponReserveResult()
    data object Exhausted : CouponReserveResult()
    data object AlreadyIssued : CouponReserveResult()
    data object NotInitialized : CouponReserveResult()
}
