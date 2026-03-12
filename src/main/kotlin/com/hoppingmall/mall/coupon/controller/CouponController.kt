package com.hoppingmall.mall.coupon.controller

import com.hoppingmall.mall.global.idempotency.Idempotent
import com.hoppingmall.mall.coupon.dto.request.CouponCreateRequest
import com.hoppingmall.mall.coupon.dto.response.CouponResponse
import com.hoppingmall.mall.coupon.dto.response.UserCouponResponse
import com.hoppingmall.mall.coupon.enum.CouponStatus
import com.hoppingmall.mall.coupon.service.CouponCommandService
import com.hoppingmall.mall.coupon.service.CouponQueryService
import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class CouponController(
    private val couponCommandService: CouponCommandService,
    private val couponQueryService: CouponQueryService
) {

    @Idempotent(ttlHours = 24)
    @PostMapping("/admin/coupons")
    fun createCoupon(
        @Valid @RequestBody request: CouponCreateRequest
    ): ResponseEntity<ApiResponse<CouponResponse>> {
        val response = couponCommandService.createCoupon(request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response))
    }

    @GetMapping("/admin/coupons")
    fun getAllCoupons(): ResponseEntity<ApiResponse<List<CouponResponse>>> {
        val response = couponQueryService.getAllCoupons()
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @Idempotent(ttlHours = 24)
    @PatchMapping("/admin/coupons/{couponId}/status")
    fun changeCouponStatus(
        @PathVariable couponId: Long,
        @RequestParam status: CouponStatus
    ): ResponseEntity<ApiResponse<CouponResponse>> {
        val response = couponCommandService.changeCouponStatus(couponId, status)
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @GetMapping("/coupons/available")
    fun getAvailableCoupons(): ResponseEntity<ApiResponse<List<CouponResponse>>> {
        val response = couponQueryService.getAvailableCoupons()
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    @Idempotent(ttlHours = 24)
    @PostMapping("/coupons/{couponId}/issue")
    fun issueCoupon(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PathVariable couponId: Long
    ): ResponseEntity<ApiResponse<UserCouponResponse>> {
        val response = couponCommandService.issueCoupon(userPrincipal.getUserId(), couponId)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response))
    }

    @GetMapping("/coupons/my")
    fun getMyCoupons(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<ApiResponse<Slice<UserCouponResponse>>> {
        val response = couponQueryService.getMyCoupons(userPrincipal.getUserId(), pageable)
        return ResponseEntity.ok(ApiResponse.success(response))
    }
}
