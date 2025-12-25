package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.mall.payment.dto.event.PointEarnRequestEvent
import com.hoppingmall.mall.notification.dto.event.NotificationEvent
import com.hoppingmall.mall.notification.enum.NotificationType
import com.hoppingmall.mall.point.service.PointPolicyService
import com.hoppingmall.mall.global.common.service.TransactionalEventPublisher
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class PaymentEventService(
    private val paymentEventPublisher: PaymentEventPublisher,
    private val transactionalEventPublisher: TransactionalEventPublisher,
    private val pointPolicyService: PointPolicyService
) {
    
    fun publishPaymentCompletedEvent(payment: Payment) {
        val event = PaymentCompletedEvent(
            paymentId = payment.id!!,
            orderId = payment.orderId,
            userId = payment.userId,
            amount = payment.amount,
            pointAmount = payment.pointAmount,
            method = payment.method,
            status = payment.status,
            transactionId = payment.transactionId!!,
            completedAt = payment.completedAt ?: LocalDateTime.now()
        )
        paymentEventPublisher.publishPaymentCompletedEvent(event)
    }
    
    fun publishPointEarnRequestEvent(payment: Payment) {
        val earnRate = getCurrentEarnRate()
        val earnAmount = payment.amount.multiply(earnRate)
        
        val event = PointEarnRequestEvent(
            userId = payment.userId,
            orderId = payment.orderId,
            paymentId = payment.id!!,
            earnAmount = earnAmount
        )
        paymentEventPublisher.publishPointEarnRequestEvent(event)
    }
    
    fun publishPaymentCompletedNotification(payment: Payment) {
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
            eventType = "PaymentCompletedNotificationRequested",
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
