package com.hoppingmall.payment.internal

import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.coupon.service.CouponQueryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/internal/api/v1/coupons")
class InternalCouponController(
    private val couponQueryService: CouponQueryService,
    private val couponCommandService: CouponCommandService
) {

    @PostMapping("/{couponId}/restore")
    fun restoreCoupon(
        @PathVariable couponId: Long,
        @RequestParam userId: Long
    ): ResponseEntity<Void> {
        couponCommandService.restoreCouponByPayment(couponId, userId)
        return ResponseEntity.ok().build()
    }
}
