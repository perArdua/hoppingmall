package com.hoppingmall.payment.refund.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.service.KafkaPaymentEventPublisher
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

@DisplayName("환불 완료의 멤버십 되돌리기 요청")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundMembershipRevertTest {

    private data class Published(
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
            published.add(Published(aggregateId, eventType, eventData as Map<*, *>, topic, partitionKey))
        }
    }

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
    private lateinit var paymentNotificationService: PaymentNotificationService

    @Spy
    private var paymentEventService: PaymentEventService = PaymentEventService(KafkaPaymentEventPublisher(recorder))

    @InjectMocks
    private lateinit var service: RefundLocalOperationService

    private fun successPayment(amount: BigDecimal): Payment {
        val payment = Payment.create(
            orderId = 100L,
            userId = 300L,
            amount = amount,
            method = PaymentMethod.CREDIT_CARD
        )
        payment.updateStatus(newStatus = PaymentStatus.SUCCESS, transactionId = "txn-refund-1")
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

    private fun membershipEvents() = published.filter { it.topic == KafkaTopics.MEMBERSHIP_UPDATE_REQUEST }

    @Test
    fun 부분_환불이_완료되면_그_환불_금액만큼_되돌리기_요청을_환불_id_기준으로_사용자_키에_낸다() {
        whenever(paymentRepository.findById(200L)).thenReturn(Optional.of(successPayment(BigDecimal("50000"))))
        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenAnswer { it.arguments[0] }

        service.execute(refundEvent(2L, BigDecimal("25000"), isFullRefund = false), eventLog(2L))

        assertThat(membershipEvents()).hasSize(1)
        val event = membershipEvents().first()
        assertThat(event.eventType).isEqualTo("MembershipRevertRequested")
        assertThat(event.aggregateId).isEqualTo("200")
        assertThat(event.partitionKey).isEqualTo("300")
        assertThat(event.eventData["eventId"]).isEqualTo("membership-revert-refund-2")
        assertThat(event.eventData["userId"]).isEqualTo(300L)
        assertThat(event.eventData["paymentId"]).isEqualTo(200L)
        assertThat(event.eventData["amount"]).isEqualTo(BigDecimal("25000"))
        assertThat(event.eventData["reason"]).isEqualTo("REFUND")
        assertThat(event.eventData["refundId"]).isEqualTo(2L)
    }

    @Test
    fun 전체_환불이_완료되면_그_환불_금액만큼_되돌리기_요청을_낸다() {
        whenever(paymentRepository.findById(200L)).thenReturn(Optional.of(successPayment(BigDecimal("50000"))))
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }
        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenAnswer { it.arguments[0] }

        service.execute(refundEvent(3L, BigDecimal("50000"), isFullRefund = true), eventLog(3L))

        assertThat(membershipEvents()).hasSize(1)
        assertThat(membershipEvents().first().eventData["eventId"]).isEqualTo("membership-revert-refund-3")
        assertThat(membershipEvents().first().eventData["amount"]).isEqualTo(BigDecimal("50000"))
    }

    @Test
    fun 포인트_단계까지_끝난_환불의_재처리는_되돌리기_요청을_다시_내지_않는다() {
        val log = eventLog(4L).apply {
            markStepCompleted(RefundEventLog.PAYMENT_UPDATED)
            markStepCompleted(RefundEventLog.COUPON_RESTORED)
            markStepCompleted(RefundEventLog.POINTS_REFUNDED)
        }
        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenAnswer { it.arguments[0] }

        service.execute(refundEvent(4L, BigDecimal("25000"), isFullRefund = false), log)

        assertThat(membershipEvents()).isEmpty()
    }

    @Test
    fun 금액이_0인_결제는_멤버십에_더하지_않았으므로_환불해도_되돌리기_요청을_내지_않는다() {
        whenever(paymentRepository.findById(200L)).thenReturn(Optional.of(successPayment(BigDecimal.ZERO)))
        whenever(refundEventLogRepository.save(any<RefundEventLog>())).thenAnswer { it.arguments[0] }

        service.execute(refundEvent(5L, BigDecimal("1000"), isFullRefund = false), eventLog(5L))

        assertThat(membershipEvents()).isEmpty()
    }
}
