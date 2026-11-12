package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.BaseEntity
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.payment.config.PaymentMetrics
import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.payment.domain.CompensationEventLog
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.service.strategy.PaymentCancellationRequestedHandler
import com.hoppingmall.payment.point.service.strategy.PointEarnRateStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("결제 취소의 거래 ID 유지")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentCancelTransactionIdTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var paymentService: PaymentService

    @Mock
    private lateinit var paymentNotificationService: PaymentNotificationService

    @Mock
    private lateinit var couponCommandService: CouponCommandService

    @Mock
    private lateinit var paymentMetrics: PaymentMetrics

    @Mock
    private lateinit var pointEarnRateStrategy: PointEarnRateStrategy

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Mock
    private lateinit var compensationEventLogService: CompensationEventLogService

    private val transactionId = "txn-cancel-1"
    private val completedAt = LocalDateTime.of(2026, 11, 13, 10, 0, 0)

    private fun commandService() = PaymentCommandServiceImpl(
        paymentRepository,
        paymentService,
        PaymentEventService(KafkaPaymentEventPublisher(transactionalEventPublisher)),
        paymentNotificationService,
        couponCommandService,
        paymentMetrics,
        pointEarnRateStrategy,
        transactionManager
    )

    private fun successPayment(): Payment {
        val payment = Payment.create(
            orderId = 100L,
            userId = 10L,
            amount = BigDecimal("3000"),
            method = PaymentMethod.CREDIT_CARD
        )
        payment.updateStatus(
            newStatus = PaymentStatus.SUCCESS,
            transactionId = transactionId,
            completedAt = completedAt
        )
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(payment, 1L)
        return payment
    }

    @Test
    fun 내부_취소_API_경로로_완료된_결제를_취소하면_거래_ID를_담은_취소_이벤트가_outbox에_한_건_쌓인다() {
        val payment = successPayment()
        whenever(paymentRepository.findByIdForUpdate(1L)).thenReturn(payment)
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }

        val result = commandService().cancelPaymentInternal(1L)

        assertThat(result.status).isEqualTo(PaymentStatus.CANCELLED)
        assertThat(result.transactionId).isEqualTo(transactionId)
        assertThat(payment.status).isEqualTo(PaymentStatus.CANCELLED)
        assertThat(payment.transactionId).isEqualTo(transactionId)
        assertThat(payment.completedAt).isEqualTo(completedAt)

        val eventTypes = argumentCaptor<String>()
        val eventData = argumentCaptor<Any>()
        verify(transactionalEventPublisher, times(1)).publishEvent(
            eq("Payment"), eq("1"), eventTypes.capture(), eventData.capture(), eq(KafkaTopics.PAYMENT_COMPENSATION), eq("100")
        )
        assertThat(eventTypes.allValues).containsExactly("PaymentCancelled")
        assertThat((eventData.firstValue as Map<*, *>)["transactionId"]).isEqualTo(transactionId)
    }

    @Test
    fun 취소_요청_처리기_경로로_완료된_결제를_취소하면_거래_ID를_담은_취소_이벤트가_outbox에_한_건_쌓인다() {
        val payment = successPayment()
        val handler = PaymentCancellationRequestedHandler(
            compensationEventLogService,
            paymentRepository,
            commandService(),
            transactionalEventPublisher
        )
        val node = ObjectMapper().createObjectNode().apply {
            put("eventId", "cancel-100-1")
            put("orderId", 100L)
        }
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any())).thenReturn(
            CompensationEventLog(
                eventId = "cancel-100-1",
                compensationType = "PAYMENT_CANCELLATION_REQUESTED",
                paymentId = 0L,
                orderId = 100L
            )
        )
        whenever(paymentRepository.findByOrderId(100L)).thenReturn(payment)
        whenever(paymentRepository.findByIdForUpdate(1L)).thenReturn(payment)
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }

        handler.handle(node)

        assertThat(payment.status).isEqualTo(PaymentStatus.CANCELLED)
        assertThat(payment.transactionId).isEqualTo(transactionId)

        val eventTypes = argumentCaptor<String>()
        val eventData = argumentCaptor<Any>()
        verify(transactionalEventPublisher, times(2)).publishEvent(
            any(), any(), eventTypes.capture(), eventData.capture(), any(), anyOrNull()
        )
        assertThat(eventTypes.allValues).containsExactly("PaymentCancelled", "PaymentCancellationCompleted")
        assertThat((eventData.firstValue as Map<*, *>)["transactionId"]).isEqualTo(transactionId)
        verify(compensationEventLogService).markCompleted("cancel-100-1")
    }
}
