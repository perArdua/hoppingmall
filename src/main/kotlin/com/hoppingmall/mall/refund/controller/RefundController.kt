package com.hoppingmall.mall.refund.controller

import com.hoppingmall.mall.global.idempotency.Idempotent
import com.hoppingmall.mall.refund.dto.request.RefundApprovalRequest
import com.hoppingmall.mall.refund.dto.request.RefundCreateRequest
import com.hoppingmall.mall.refund.dto.response.RefundResponse
import com.hoppingmall.mall.refund.service.RefundCommandService
import com.hoppingmall.mall.refund.service.RefundQueryService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/refunds")
class RefundController(
    private val refundCommandService: RefundCommandService,
    private val refundQueryService: RefundQueryService
) {

    @Idempotent(ttlHours = 24)
    @PostMapping
    fun requestRefund(
        @Valid @RequestBody request: RefundCreateRequest,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<RefundResponse> {
        val buyerId = userDetails.username.toLong()
        val response = refundCommandService.requestRefund(buyerId, request)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{refundId}")
    fun getRefund(
        @PathVariable refundId: Long,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<RefundResponse> {
        val userId = userDetails.username.toLong()
        val response = refundQueryService.getRefund(refundId, userId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/my")
    fun getMyRefunds(
        @AuthenticationPrincipal userDetails: UserDetails,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Page<RefundResponse>> {
        val buyerId = userDetails.username.toLong()
        val pageable = PageRequest.of(page, size)
        val refunds = refundQueryService.getMyRefunds(buyerId, pageable)
        return ResponseEntity.ok(refunds)
    }

    @GetMapping("/seller")
    fun getSellerRefunds(
        @AuthenticationPrincipal userDetails: UserDetails,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Page<RefundResponse>> {
        val sellerId = userDetails.username.toLong()
        val pageable = PageRequest.of(page, size)
        val refunds = refundQueryService.getSellerRefunds(sellerId, pageable)
        return ResponseEntity.ok(refunds)
    }

    @PatchMapping("/{refundId}/approve")
    fun approveRefund(
        @PathVariable refundId: Long,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<RefundResponse> {
        val approverId = userDetails.username.toLong()
        val response = refundCommandService.approveRefund(refundId, approverId)
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/{refundId}/reject")
    fun rejectRefund(
        @PathVariable refundId: Long,
        @RequestBody request: RefundApprovalRequest,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<RefundResponse> {
        val approverId = userDetails.username.toLong()
        val response = refundCommandService.rejectRefund(refundId, approverId, request)
        return ResponseEntity.ok(response)
    }
}
