package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.domain.repository.PaymentRepository
import com.hoppingmall.mall.payment.dto.PaymentResult
import com.hoppingmall.mall.payment.enum.PaymentStatus
import com.hoppingmall.mall.payment.dto.request.PaymentRequest
import com.hoppingmall.mall.payment.dto.response.PaymentResponse
import com.hoppingmall.mall.payment.exception.PaymentInvalidStateException
import com.hoppingmall.mall.global.common.service.TransactionalEventPublisher
import com.hoppingmall.mall.point.service.PointPolicyService
import com.hoppingmall.mall.notification.enum.NotificationType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class PaymentCommandServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentService: PaymentService,
    private val transactionalEventPublisher: TransactionalEventPublisher,
    private val pointPolicyService: PointPolicyService
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
        transactionalEventPublisher.publishEvent(
            aggregateType = "Payment",
            aggregateId = payment.id!!.toString(),
            eventType = "PaymentCompleted",
            eventData = mapOf(
                "paymentId" to payment.id!!,
                "orderId" to payment.orderId,
                "userId" to payment.userId,
                "amount" to payment.amount,
                "method" to payment.method,
                "status" to payment.status,
                "transactionId" to payment.transactionId,
                "completedAt" to payment.completedAt
            ),
            topic = "payment",
            partitionKey = payment.orderId.toString()
        )
        
        if (payment.amount > BigDecimal.ZERO) {
            val earnRate = getCurrentEarnRate()
            val earnAmount = payment.amount.multiply(earnRate)
            
            transactionalEventPublisher.publishEvent(
                aggregateType = "Payment",
                aggregateId = payment.id!!.toString(),
                eventType = "PointEarnRequested", 
                eventData = mapOf(
                    "userId" to payment.userId,
                    "paymentId" to payment.id!!,
                    "orderId" to payment.orderId,
                    "earnAmount" to earnAmount
                ),
                topic = "point-earn-request",
                partitionKey = payment.userId.toString()
            )
        }
        
        val metadata = mapOf(
            "orderId" to payment.orderId,
            "paymentId" to payment.id!!,
            "amount" to payment.amount.toString(),
            "method" to payment.method.toString(),
            "transactionId" to payment.transactionId
        )
        
        transactionalEventPublisher.publishEvent(
            aggregateType = "Payment",
            aggregateId = payment.id!!.toString(),
            eventType = "PaymentNotificationRequested",
            eventData = mapOf(
                "userId" to payment.userId,
                "type" to NotificationType.PAYMENT_COMPLETED.toString(),
                "title" to "결제가 완료되었습니다",
                "content" to "주문번호 ${payment.orderId}의 결제가 성공적으로 완료되었습니다. 결제 금액: ${payment.amount}원",
                "metadata" to metadata
            ),
            topic = "notification",
            partitionKey = payment.userId.toString()
        )
    }
    
    private fun getCurrentEarnRate(): BigDecimal {
        val currentPolicy = pointPolicyService.getCurrentPolicy()
        return currentPolicy?.earnRate ?: BigDecimal("0.01") // 기본값 1%
    }
} 