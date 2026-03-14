package com.hoppingmall.payment.payment.controller

import com.hoppingmall.payment.payment.dto.request.PaymentRequest
import com.hoppingmall.payment.payment.dto.response.PaymentResponse
import com.hoppingmall.payment.payment.service.PaymentCommandService
import com.hoppingmall.payment.payment.service.PaymentQueryService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import com.hoppingmall.payment.common.UserPrincipal
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
    ): ResponseEntity<PaymentResponse> {
        val userId = principal.getUserId()
        val paymentResponse = paymentCommandService.processPayment(paymentRequest, userId)
        return ResponseEntity.ok(paymentResponse)
    }

    @GetMapping("/{paymentId}")
    fun getPaymentById(
        @PathVariable paymentId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PaymentResponse> {
        val userId = principal.getUserId()
        val paymentResponse = paymentQueryService.getPaymentById(paymentId, userId)
        return ResponseEntity.ok(paymentResponse)
    }

    @GetMapping
    fun getMyPayments(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Page<PaymentResponse>> {
        val userId = principal.getUserId()
        val pageable = PageRequest.of(page, size)
        val payments = paymentQueryService.getPaymentsByUserId(userId, pageable)
        return ResponseEntity.ok(payments)
    }

    @GetMapping("/order/{orderId}")
    fun getPaymentsByOrderId(
        @PathVariable orderId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<List<PaymentResponse>> {
        val userId = principal.getUserId()
        val payments = paymentQueryService.getPaymentsByOrderId(orderId, userId)
        return ResponseEntity.ok(payments)
    }

    @PostMapping("/{paymentId}/cancel")
    fun cancelPayment(
        @PathVariable paymentId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<PaymentResponse> {
        val userId = principal.getUserId()
        val paymentResponse = paymentCommandService.cancelPayment(paymentId, userId)
        return ResponseEntity.ok(paymentResponse)
    }
}
