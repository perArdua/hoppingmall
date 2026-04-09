package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.BaseEntity
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@DisplayName("PaymentNotificationService")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentNotificationServiceTest {

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Mock
    private lateinit var notificationEventFactory: NotificationEventFactory

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @InjectMocks
    private lateinit var service: PaymentNotificationService

    private fun createPayment(id: Long = 1L, userId: Long = 10L): Payment {
        val payment = Payment.create(
            orderId = 100L,
            userId = userId,
            amount = BigDecimal("50000"),
            method = PaymentMethod.CREDIT_CARD
        )
        payment.updateStatus(PaymentStatus.SUCCESS, transactionId = "txn-test")
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(payment, id)
        return payment
    }

    @Test
    fun publishPaymentCompletedNotification_이벤트_퍼블리셔가_올바른_토픽과_이벤트타입으로_호출된다() {
        val payment = createPayment()
        whenever(objectMapper.writeValueAsString(any())).thenReturn("{\"metadata\":\"test\"}")
        whenever(notificationEventFactory.createNotificationEventData(any(), any(), any(), any(), any(), any()))
            .thenReturn(mapOf("eventId" to "txn-test"))

        service.publishPaymentCompletedNotification(payment)

        verify(transactionalEventPublisher).publishEvent(
            aggregateType = eq("Payment"),
            aggregateId = eq("1"),
            eventType = eq("PaymentCompletedNotificationRequested"),
            eventData = any(),
            topic = eq(KafkaTopics.NOTIFICATION),
            partitionKey = eq("10")
        )
    }

    @Test
    fun publishPaymentFailedNotification_이벤트_퍼블리셔가_올바른_토픽과_이벤트타입으로_호출된다() {
        val payment = createPayment()
        whenever(objectMapper.writeValueAsString(any())).thenReturn("{\"metadata\":\"test\"}")
        whenever(notificationEventFactory.createNotificationEventData(any(), any(), any(), any(), any(), any()))
            .thenReturn(mapOf("eventId" to "payment-failed-notification-1"))

        service.publishPaymentFailedNotification(payment)

        verify(transactionalEventPublisher).publishEvent(
            aggregateType = eq("Payment"),
            aggregateId = eq("1"),
            eventType = eq("PaymentFailedNotificationRequested"),
            eventData = any(),
            topic = eq(KafkaTopics.NOTIFICATION),
            partitionKey = eq("10")
        )
    }

    @Test
    fun publishPaymentCancelledNotification_이벤트_퍼블리셔가_올바른_토픽과_이벤트타입으로_호출된다() {
        val payment = createPayment()
        whenever(objectMapper.writeValueAsString(any())).thenReturn("{\"metadata\":\"test\"}")
        whenever(notificationEventFactory.createNotificationEventData(any(), any(), any(), any(), any(), any()))
            .thenReturn(mapOf("eventId" to "payment-cancelled-notification-1"))

        service.publishPaymentCancelledNotification(payment)

        verify(transactionalEventPublisher).publishEvent(
            aggregateType = eq("Payment"),
            aggregateId = eq("1"),
            eventType = eq("PaymentCancelledNotificationRequested"),
            eventData = any(),
            topic = eq(KafkaTopics.NOTIFICATION),
            partitionKey = eq("10")
        )
    }
}
