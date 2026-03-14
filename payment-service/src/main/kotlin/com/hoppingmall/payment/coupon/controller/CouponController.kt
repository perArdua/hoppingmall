package com.hoppingmall.payment.coupon.controller

import com.hoppingmall.payment.coupon.dto.request.CouponCreateRequest
import com.hoppingmall.payment.coupon.dto.response.CouponResponse
import com.hoppingmall.payment.coupon.dto.response.UserCouponResponse
import com.hoppingmall.payment.coupon.enum.CouponStatus
import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.coupon.service.CouponQueryService
import com.hoppingmall.payment.common.UserPrincipal
import com.hoppingmall.payment.common.ApiResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
@Tag(name = "쿠폰")
class CouponController(
    private val couponCommandService: CouponCommandService,
    private val couponQueryService: CouponQueryService
) {

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
