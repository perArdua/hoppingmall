package com.hoppingmall.payment.internal

import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.dto.response.PaymentResponse
import com.hoppingmall.payment.payment.service.PaymentCommandService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/internal/api/v1/payments")
class InternalPaymentController(
    private val paymentRepository: PaymentRepository,
    private val paymentCommandService: PaymentCommandService
) {

    @GetMapping("/by-order/{orderId}")
    fun getPaymentByOrderId(@PathVariable orderId: Long): ResponseEntity<PaymentInfoResponse> {
        val payment = paymentRepository.findByOrderId(orderId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toResponse(payment))
    }

    @GetMapping("/{id}")
    fun getPaymentById(@PathVariable id: Long): ResponseEntity<PaymentInfoResponse> {
        val payment = paymentRepository.findByIdOrNull(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toResponse(payment))
    }

    @PostMapping("/by-order/{orderId}/cancel")
    fun cancelPaymentByOrderId(@PathVariable orderId: Long): ResponseEntity<PaymentResponse> {
        val payment = paymentRepository.findByOrderId(orderId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(paymentCommandService.cancelPaymentInternal(payment.id!!))
    }

    private fun toResponse(payment: com.hoppingmall.payment.payment.domain.Payment): PaymentInfoResponse {
        return PaymentInfoResponse(
            id = payment.id!!,
            orderId = payment.orderId,
            amount = payment.amount,
            pointAmount = payment.pointAmount,
            couponId = payment.couponId,
            status = payment.status.name
        )
    }

    data class PaymentInfoResponse(
        val id: Long,
        val orderId: Long,
        val amount: BigDecimal,
        val pointAmount: BigDecimal,
        val couponId: Long?,
        val status: String
    )
}
