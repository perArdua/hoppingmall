package com.hoppingmall.mall.payment.controller

import com.hoppingmall.mall.payment.dto.request.PaymentRequest
import com.hoppingmall.mall.payment.dto.response.PaymentResponse
import com.hoppingmall.mall.payment.service.PaymentCommandService
import com.hoppingmall.mall.payment.service.PaymentQueryService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentCommandService: PaymentCommandService,
    private val paymentQueryService: PaymentQueryService
) {
    
    @PostMapping
    fun processPayment(
        @Valid @RequestBody paymentRequest: PaymentRequest,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<PaymentResponse> {
        val userId = userDetails.username.toLong()
        val paymentResponse = paymentCommandService.processPayment(paymentRequest, userId)
        return ResponseEntity.ok(paymentResponse)
    }
    
    @GetMapping("/{paymentId}")
    fun getPaymentById(@PathVariable paymentId: Long): ResponseEntity<PaymentResponse> {
        val paymentResponse = paymentQueryService.getPaymentById(paymentId)
        return ResponseEntity.ok(paymentResponse)
    }
    
    @GetMapping
    fun getMyPayments(
        @AuthenticationPrincipal userDetails: UserDetails,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Page<PaymentResponse>> {
        val userId = userDetails.username.toLong()
        val pageable = PageRequest.of(page, size)
        val payments = paymentQueryService.getPaymentsByUserId(userId, pageable)
        return ResponseEntity.ok(payments)
    }
    
    @GetMapping("/order/{orderId}")
    fun getPaymentsByOrderId(@PathVariable orderId: Long): ResponseEntity<List<PaymentResponse>> {
        val payments = paymentQueryService.getPaymentsByOrderId(orderId)
        return ResponseEntity.ok(payments)
    }
} 