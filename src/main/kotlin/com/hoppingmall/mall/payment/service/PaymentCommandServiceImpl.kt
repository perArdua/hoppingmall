package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.domain.repository.PaymentRepository
import com.hoppingmall.mall.payment.dto.PaymentResult
import com.hoppingmall.mall.payment.enum.PaymentStatus
import com.hoppingmall.mall.payment.dto.request.PaymentRequest
import com.hoppingmall.mall.payment.dto.response.PaymentResponse
import com.hoppingmall.mall.payment.exception.PaymentInvalidStateException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class PaymentCommandServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentService: PaymentService,
    private val paymentEventService: PaymentEventService
) : PaymentCommandService {
    
    override fun processPayment(paymentRequest: PaymentRequest, userId: Long): PaymentResponse {
        val payment = Payment.create(
            orderId = paymentRequest.orderId,
            userId = userId,
            amount = paymentRequest.amount,
            method = paymentRequest.method,
            pointAmount = paymentRequest.pointAmount
        )
        
        val savedPayment = paymentRepository.save(payment)
        
        val paymentResult = paymentService.processPayment(savedPayment)
        
        val updatedPayment = updatePaymentWithResult(savedPayment, paymentResult)
        
        val finalPayment = paymentRepository.save(updatedPayment)
        
        if (finalPayment.status == PaymentStatus.SUCCESS) {
            publishPaymentEvents(finalPayment)
        }
        
        return PaymentResponse.from(finalPayment)
    }
    
    private fun updatePaymentWithResult(payment: Payment, result: PaymentResult): Payment {
        return payment.apply {
            if (result.isSuccess) {
                status = PaymentStatus.SUCCESS
                transactionId = result.transactionId
                completedAt = result.completedAt
            } else {
                status = PaymentStatus.FAILED
                errorMessage = result.errorMessage
            }
        }
    }
    
    private fun publishPaymentEvents(payment: Payment) {
        paymentEventService.publishPaymentCompletedEvent(payment)
        paymentEventService.publishPointEarnRequestEvent(payment)
        paymentEventService.publishPaymentCompletedNotification(payment)
    }
} 