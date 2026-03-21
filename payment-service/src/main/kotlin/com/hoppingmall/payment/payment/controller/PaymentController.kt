package com.hoppingmall.payment.payment.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.payment.payment.dto.request.PaymentRequest
import com.hoppingmall.payment.payment.dto.response.PaymentResponse
import com.hoppingmall.payment.payment.service.PaymentCommandService
import com.hoppingmall.payment.payment.service.PaymentQueryService
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import com.hoppingmall.common.UserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "결제")
class PaymentController(
    private val paymentCommandService: PaymentCommandService,
    private val paymentQueryService: PaymentQueryService
) {

    @PostMapping
    fun processPayment(
        @Valid @RequestBody paymentRequest: PaymentRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<PaymentResponse> {
        val userId = principal.getUserId()
        val paymentResponse = paymentCommandService.processPayment(paymentRequest, userId)
        return ApiResponse.success(paymentResponse)
    }

    @GetMapping("/{paymentId}")
    fun getPaymentById(
        @PathVariable paymentId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<PaymentResponse> {
        val userId = principal.getUserId()
        val paymentResponse = paymentQueryService.getPaymentById(paymentId, userId)
        return ApiResponse.success(paymentResponse)
    }

    @GetMapping
    fun getMyPayments(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ApiResponse<Slice<PaymentResponse>> {
        val userId = principal.getUserId()
        val pageable = PageRequest.of(page, size)
        val payments = paymentQueryService.getPaymentsByUserId(userId, pageable)
        return ApiResponse.success(payments)
    }

    @GetMapping("/order/{orderId}")
    fun getPaymentsByOrderId(
        @PathVariable orderId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<List<PaymentResponse>> {
        val userId = principal.getUserId()
        val payments = paymentQueryService.getPaymentsByOrderId(orderId, userId)
        return ApiResponse.success(payments)
    }

    @PostMapping("/{paymentId}/cancel")
    fun cancelPayment(
        @PathVariable paymentId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ApiResponse<PaymentResponse> {
        val userId = principal.getUserId()
        val paymentResponse = paymentCommandService.cancelPayment(paymentId, userId)
        return ApiResponse.success(paymentResponse)
    }
}
