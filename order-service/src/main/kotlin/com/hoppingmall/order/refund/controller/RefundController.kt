package com.hoppingmall.order.refund.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.idempotency.Idempotent
import com.hoppingmall.order.refund.dto.request.RefundApprovalRequest
import com.hoppingmall.order.refund.dto.request.RefundCreateRequest
import com.hoppingmall.order.refund.dto.response.RefundResponse
import com.hoppingmall.order.refund.service.RefundCommandService
import com.hoppingmall.order.refund.service.RefundQueryService
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
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
    ): ApiResponse<RefundResponse> {
        val buyerId = principal.getUserId()
        return ApiResponse.success(refundCommandService.requestRefund(buyerId, request))
    }

    @GetMapping("/{refundId}")
    fun getRefund(
        @PathVariable refundId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<RefundResponse> {
        val userId = principal.getUserId()
        return ApiResponse.success(refundQueryService.getRefund(refundId, userId))
    }

    @GetMapping("/my")
    fun getMyRefunds(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ApiResponse<Slice<RefundResponse>> {
        val buyerId = principal.getUserId()
        val pageable = PageRequest.of(page, size)
        return ApiResponse.success(refundQueryService.getMyRefunds(buyerId, pageable))
    }

    @GetMapping("/seller")
    fun getSellerRefunds(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ApiResponse<Slice<RefundResponse>> {
        val sellerId = principal.getUserId()
        val pageable = PageRequest.of(page, size)
        return ApiResponse.success(refundQueryService.getSellerRefunds(sellerId, pageable))
    }

    @Idempotent(ttlHours = 24)
    @PatchMapping("/{refundId}/approve")
    fun approveRefund(
        @PathVariable refundId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<RefundResponse> {
        val approverId = principal.getUserId()
        return ApiResponse.success(refundCommandService.approveRefund(refundId, approverId))
    }

    @Idempotent(ttlHours = 24)
    @PatchMapping("/{refundId}/reject")
    fun rejectRefund(
        @PathVariable refundId: Long,
        @RequestBody request: RefundApprovalRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<RefundResponse> {
        val approverId = principal.getUserId()
        return ApiResponse.success(refundCommandService.rejectRefund(refundId, approverId, request))
    }
}
