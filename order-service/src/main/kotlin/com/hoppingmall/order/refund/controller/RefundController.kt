package com.hoppingmall.order.refund.controller

import com.hoppingmall.order.common.UserPrincipal
import com.hoppingmall.order.common.idempotency.Idempotent
import com.hoppingmall.order.refund.dto.request.RefundApprovalRequest
import com.hoppingmall.order.refund.dto.request.RefundCreateRequest
import com.hoppingmall.order.refund.dto.response.RefundResponse
import com.hoppingmall.order.refund.service.RefundCommandService
import com.hoppingmall.order.refund.service.RefundQueryService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/refunds")
@Tag(name = "환불")
class RefundController(
    private val refundCommandService: RefundCommandService,
    private val refundQueryService: RefundQueryService
) {

    @Idempotent(ttlHours = 24)
    @PostMapping
    fun requestRefund(
        @Valid @RequestBody request: RefundCreateRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<RefundResponse> {
        val buyerId = principal.getUserId()
        val response = refundCommandService.requestRefund(buyerId, request)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{refundId}")
    fun getRefund(
        @PathVariable refundId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<RefundResponse> {
        val userId = principal.getUserId()
        val response = refundQueryService.getRefund(refundId, userId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/my")
    fun getMyRefunds(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Page<RefundResponse>> {
        val buyerId = principal.getUserId()
        val pageable = PageRequest.of(page, size)
        val refunds = refundQueryService.getMyRefunds(buyerId, pageable)
        return ResponseEntity.ok(refunds)
    }

    @GetMapping("/seller")
    fun getSellerRefunds(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Page<RefundResponse>> {
        val sellerId = principal.getUserId()
        val pageable = PageRequest.of(page, size)
        val refunds = refundQueryService.getSellerRefunds(sellerId, pageable)
        return ResponseEntity.ok(refunds)
    }

    @Idempotent(ttlHours = 24)
    @PatchMapping("/{refundId}/approve")
    fun approveRefund(
        @PathVariable refundId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<RefundResponse> {
        val approverId = principal.getUserId()
        val response = refundCommandService.approveRefund(refundId, approverId)
        return ResponseEntity.ok(response)
    }

    @Idempotent(ttlHours = 24)
    @PatchMapping("/{refundId}/reject")
    fun rejectRefund(
        @PathVariable refundId: Long,
        @RequestBody request: RefundApprovalRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<RefundResponse> {
        val approverId = principal.getUserId()
        val response = refundCommandService.rejectRefund(refundId, approverId, request)
        return ResponseEntity.ok(response)
    }
}
