package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.common.NotificationType
import com.hoppingmall.payment.outbox.service.TransactionalEventPublisher
import org.springframework.stereotype.Service

@Service
class PaymentNotificationService(
    private val transactionalEventPublisher: TransactionalEventPublisher,
    private val notificationEventFactory: NotificationEventFactory,
    private val objectMapper: ObjectMapper
) {

    fun publishPaymentCompletedNotification(payment: Payment) {
        val eventId = payment.transactionId ?: "payment-${payment.id}"
        val metadata = objectMapper.writeValueAsString(
            mapOf(
                "orderId" to payment.orderId,
                "paymentId" to payment.id!!,
                "amount" to payment.amount.toString(),
                "method" to payment.method.toString(),
                "transactionId" to payment.transactionId
            )
        )

        val eventData = notificationEventFactory.createNotificationEventData(
            eventId = eventId,
            userId = payment.userId,
            type = NotificationType.PAYMENT_COMPLETED,
            title = "결제가 완료되었습니다",
            content = "주문번호 ${payment.orderId}의 결제가 성공적으로 완료되었습니다. 결제 금액: ${payment.amount}원",
            metadata = metadata
        )

        transactionalEventPublisher.publishEvent(
            aggregateType = "Payment",
            aggregateId = payment.id!!.toString(),
            eventType = "PaymentCompletedNotificationRequested",
            eventData = eventData,
            topic = KafkaTopics.NOTIFICATION,
            partitionKey = payment.userId.toString()
        )
    }

    fun publishPaymentFailedNotification(payment: Payment) {
        val eventId = "payment-failed-notification-${payment.id}"
        val metadata = objectMapper.writeValueAsString(
            mapOf(
                "orderId" to payment.orderId,
                "paymentId" to payment.id!!,
                "amount" to payment.amount.toString(),
                "reason" to (payment.errorMessage ?: "결제 실패")
            )
        )

        val eventData = notificationEventFactory.createNotificationEventData(
            eventId = eventId,
            userId = payment.userId,
            type = NotificationType.PAYMENT_FAILED,
            title = "결제가 실패했습니다",
            content = "주문번호 ${payment.orderId}의 결제가 실패했습니다. 사유: ${payment.errorMessage ?: "결제 실패"}",
            metadata = metadata
        )

        transactionalEventPublisher.publishEvent(
            aggregateType = "Payment",
            aggregateId = payment.id!!.toString(),
            eventType = "PaymentFailedNotificationRequested",
            eventData = eventData,
            topic = KafkaTopics.NOTIFICATION,
            partitionKey = payment.userId.toString()
        )
    }

    fun publishPaymentCancelledNotification(payment: Payment) {
        val eventId = "payment-cancelled-notification-${payment.id}"
        val metadata = objectMapper.writeValueAsString(
            mapOf(
                "orderId" to payment.orderId,
                "paymentId" to payment.id!!,
                "amount" to payment.amount.toString(),
                "transactionId" to payment.transactionId
            )
        )

        val eventData = notificationEventFactory.createNotificationEventData(
            eventId = eventId,
            userId = payment.userId,
            type = NotificationType.PAYMENT_CANCELLED,
            title = "결제가 취소되었습니다",
            content = "주문번호 ${payment.orderId}의 결제가 취소되었습니다. 취소 금액: ${payment.amount}원",
            metadata = metadata
        )

        transactionalEventPublisher.publishEvent(
            aggregateType = "Payment",
            aggregateId = payment.id!!.toString(),
            eventType = "PaymentCancelledNotificationRequested",
            eventData = eventData,
            topic = KafkaTopics.NOTIFICATION,
            partitionKey = payment.userId.toString()
        )
    }
}
