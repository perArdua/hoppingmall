package com.hoppingmall.mall.payment.controller

import com.hoppingmall.mall.payment.domain.repository.PaymentRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/internal/api/v1/payments")
class InternalPaymentController(
    private val paymentRepository: PaymentRepository
) {

    @GetMapping("/by-order/{orderId}")
    fun getPaymentByOrderId(@PathVariable orderId: Long): ResponseEntity<PaymentInfoResponse> {
        val payment = paymentRepository.findByOrderId(orderId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toResponse(payment))
    }

    @GetMapping("/{id}")
    fun getPaymentById(@PathVariable id: Long): ResponseEntity<PaymentInfoResponse> {
        val payment = paymentRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toResponse(payment))
    }

    private fun toResponse(payment: com.hoppingmall.mall.payment.domain.Payment): PaymentInfoResponse {
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
