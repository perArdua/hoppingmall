package com.hoppingmall.payment.refund.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.BaseEntity
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.service.NotificationEventFactory
import com.hoppingmall.payment.payment.service.PaymentEventService
import com.hoppingmall.payment.payment.service.PaymentNotificationService
import com.hoppingmall.payment.payment.service.RefundPointsService
import com.hoppingmall.payment.point.service.PointCommandService
import com.hoppingmall.payment.refund.domain.RefundEventLog
import com.hoppingmall.payment.refund.domain.repository.RefundEventLogRepository
import com.hoppingmall.payment.refund.dto.event.RefundCompletedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional

@DisplayName("환불 완료의 구매자 환불 알림 요청")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundCompletedNotificationTest {

    private data class Published(
        val aggregateType: String,
        val aggregateId: String,
        val eventType: String,
        val eventData: Map<*, *>,
        val topic: String,
        val partitionKey: String?
    )

    private val published = mutableListOf<Published>()

    private val recorder = object : TransactionalEventPublisher {
        override fun publishEvent(
            aggregateType: String,
            aggregateId: String,
            eventType: String,
            eventData: Any,
            topic: String,
            partitionKey: String?
        ) {
            published.add(Published(aggregateType, aggregateId, eventType, eventData as Map<*, *>, topic, partitionKey))
        }
    }

    private val objectMapper = ObjectMapper()

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var pointCommandService: PointCommandService

    @Mock
    private lateinit var couponCommandService: CouponCommandService

    @Mock
    private lateinit var refundEventLogRepository: RefundEventLogRepository

    @Mock
    private lateinit var refundPointsService: RefundPointsService

    @Mock
    private lateinit var paymentEventService: PaymentEventService

    @Spy
    private var paymentNotificationService: PaymentNotificationService =
        PaymentNotificationService(recorder, NotificationEventFactory(), objectMapper)

    @InjectMocks
    private lateinit var service: RefundLocalOperationService

    private fun successPayment(amount: BigDecimal): Payment {
        val payment = Payment.create(
            orderId = 100L,
            userId = 300L,
            amount = amount,
            method = PaymentMethod.CREDIT_CARD
        )
        payment.updateStatus(newStatus = PaymentStatus.SUCCESS, transactionId = "txn-refund-777")
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(payment, 200L)
        return payment
    }

    private fun refundEvent(refundId: Long, refundAmount: BigDecimal, isFullRefund: Boolean) = RefundCompletedEvent(
        eventId = "refund-evt-$refundId",
        refundId = refundId,
        orderId = 100L,
        paymentId = 200L,
        buyerId = 300L,
        refundAmount = refundAmount,
        pointRefundAmount = BigDecimal.ZERO,
        isFullRefund = isFullRefund
    )

    private fun eventLog(refundId: Long) = RefundEventLog(
        eventId = "refund-evt-$refundId",
        eventType = "REFUND_COMPLETED",
        refundId = refundId,
        orderId = 100L
    )

    private fun notifications() = published.filter { it.topic == KafkaTopics.NOTIFICATION }

    private fun metadata(event: Published): Map<*, *> =
        objectMapper.readValue(event.eventData["metadata"] as String, Map::class.java)

    @Test
    fun 부분_환불이_완료되면_환불_금액으로_환불_완료_알림을_구매자에게_한_건_낸다() {
        whenever(paymentRepository.findById(200L)).thenReturn(Optional.of(successPayment(BigDecimal("50000"))))
        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenAnswer { it.arguments[0] }

        service.execute(refundEvent(2L, BigDecimal("19666.67"), isFullRefund = false), eventLog(2L))

        assertThat(notifications()).hasSize(1)
        val event = notifications().first()
        assertThat(event.eventType).isEqualTo("RefundCompletedNotificationRequested")
        assertThat(event.aggregateType).isEqualTo("Payment")
        assertThat(event.aggregateId).isEqualTo("200")
        assertThat(event.partitionKey).isEqualTo("300")
        assertThat(event.eventData["eventId"]).isEqualTo("refund-completed-notification-2")
        assertThat(event.eventData["userId"]).isEqualTo(300L)
        assertThat(event.eventData["type"]).isEqualTo("REFUND_COMPLETED")
        assertThat(event.eventData["content"] as String).contains("100").contains("19666.67")
        assertThat(metadata(event)["orderId"]).isEqualTo(100)
        assertThat(metadata(event)["paymentId"]).isEqualTo(200)
        assertThat(metadata(event)["amount"]).isEqualTo("19666.67")
        assertThat(metadata(event)["transactionId"]).isEqualTo("txn-refund-777")
    }

    @Test
    fun 전체_환불도_결제_금액이_아니라_이번_환불_금액으로_한_건_낸다() {
        whenever(paymentRepository.findById(200L)).thenReturn(Optional.of(successPayment(BigDecimal("50000"))))
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }
        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenAnswer { it.arguments[0] }

        service.execute(refundEvent(3L, BigDecimal("30333.33"), isFullRefund = true), eventLog(3L))

        assertThat(notifications()).hasSize(1)
        assertThat(notifications().first().eventData["eventId"]).isEqualTo("refund-completed-notification-3")
        assertThat(metadata(notifications().first())["amount"]).isEqualTo("30333.33")
    }

    @Test
    fun 환불_알림은_결제_취소_알림과_같은_토픽_같은_필드_모양이다() {
        whenever(paymentRepository.findById(200L)).thenReturn(Optional.of(successPayment(BigDecimal("50000"))))
        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenAnswer { it.arguments[0] }

        service.execute(refundEvent(4L, BigDecimal("1000"), isFullRefund = false), eventLog(4L))
        PaymentNotificationService(recorder, NotificationEventFactory(), objectMapper)
            .publishPaymentCancelledNotification(successPayment(BigDecimal("50000")))

        val refund = published.single { it.eventType == "RefundCompletedNotificationRequested" }
        val cancel = published.single { it.eventType == "PaymentCancelledNotificationRequested" }
        assertThat(refund.topic).isEqualTo(cancel.topic)
        assertThat(refund.partitionKey).isEqualTo(cancel.partitionKey)
        assertThat(refund.eventData.keys).isEqualTo(cancel.eventData.keys)
        assertThat(metadata(refund).keys).isEqualTo(metadata(cancel).keys)
    }

    @Test
    fun 포인트_단계까지_끝난_환불의_재처리는_알림을_다시_내지_않는다() {
        val log = eventLog(5L).apply {
            markStepCompleted(RefundEventLog.PAYMENT_UPDATED)
            markStepCompleted(RefundEventLog.COUPON_RESTORED)
            markStepCompleted(RefundEventLog.POINTS_REFUNDED)
        }
        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenAnswer { it.arguments[0] }

        service.execute(refundEvent(5L, BigDecimal("1000"), isFullRefund = false), log)

        assertThat(notifications()).isEmpty()
    }
}
