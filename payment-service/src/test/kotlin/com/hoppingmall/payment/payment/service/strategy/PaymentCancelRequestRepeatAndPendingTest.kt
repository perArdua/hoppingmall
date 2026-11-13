package com.hoppingmall.payment.payment.service.strategy

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.BaseEntity
import com.hoppingmall.common.BusinessException
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.payment.config.PaymentMetrics
import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.payment.domain.CompensationEventLog
import com.hoppingmall.payment.payment.domain.CompensationEventLogStatus
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.CompensationEventLogRepository
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.service.CompensationEventLogService
import com.hoppingmall.payment.payment.service.PaymentCommandServiceImpl
import com.hoppingmall.payment.payment.service.PaymentEventPublisher
import com.hoppingmall.payment.payment.service.PaymentEventService
import com.hoppingmall.payment.payment.service.PaymentNotificationService
import com.hoppingmall.payment.payment.service.PaymentService
import com.hoppingmall.payment.point.service.strategy.PointEarnRateStrategy
import com.hoppingmall.payment.support.FakeOrderSummaryQueryPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("결제 취소 요청의 반복과 진행 중 결제")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentCancelRequestRepeatAndPendingTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var paymentService: PaymentService

    @Mock
    private lateinit var paymentEventPublisher: PaymentEventPublisher

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
    private lateinit var compensationEventLogRepository: CompensationEventLogRepository

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    private val logs = mutableMapOf<String, CompensationEventLog>()

    private val objectMapper = ObjectMapper()

    private lateinit var service: PaymentCommandServiceImpl

    private lateinit var handler: PaymentCancellationRequestedHandler

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(compensationEventLogRepository.findByEventId(any())).thenAnswer {
            logs[it.getArgument<String>(0)]
        }
        Mockito.lenient().`when`(compensationEventLogRepository.save(any<CompensationEventLog>())).thenAnswer {
            val log = it.getArgument<CompensationEventLog>(0)
            logs[log.eventId] = log
            log
        }
        Mockito.lenient().`when`(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }
        service = PaymentCommandServiceImpl(
            paymentRepository,
            paymentService,
            PaymentEventService(paymentEventPublisher),
            paymentNotificationService,
            couponCommandService,
            paymentMetrics,
            pointEarnRateStrategy,
            FakeOrderSummaryQueryPort(),
            transactionManager
        )
        handler = PaymentCancellationRequestedHandler(
            CompensationEventLogService(compensationEventLogRepository),
            paymentRepository,
            service,
            transactionalEventPublisher
        )
    }

    @Test
    fun 진행_중인_결제의_내부_취소는_NPE_없이_지금은_취소할_수_없다고_거절하고_결제는_그대로_둔다() {
        val payment = stored(pendingPayment(id = 1L, orderId = 100L))

        val thrown = catchThrowable { service.cancelPaymentInternal(1L) }

        assertThat(thrown).isInstanceOf(BusinessException::class.java)
        assertThat((thrown as BusinessException).errorCode.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        verify(paymentRepository, never()).save(any<Payment>())
        verify(paymentEventPublisher, never()).publishPaymentCancelledEvent(any())
        verify(couponCommandService, never()).restoreCouponByOrder(any())
    }

    @Test
    fun 진행_중인_결제에_온_취소_요청_이벤트는_NPE_없이_취소_실패로_답하고_결제는_그대로_둔다() {
        val payment = stored(pendingPayment(id = 2L, orderId = 200L))

        handler.handle(request("cancel-200-1", 200L))

        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        verify(transactionalEventPublisher, times(1)).publishEvent(
            eq("Payment"), eq("200"), eq("PaymentCancellationFailed"), any(), any(), eq("200")
        )
        verify(transactionalEventPublisher, never()).publishEvent(
            any(), any(), eq("PaymentCancellationCompleted"), any(), any(), anyOrNull()
        )
        verify(paymentEventPublisher, never()).publishPaymentCancelledEvent(any())
        assertThat(logs["cancel-200-1"]!!.status).isEqualTo(CompensationEventLogStatus.COMPLETED)
    }

    @Test
    fun 이미_취소된_결제에_온_취소_요청_이벤트는_실패가_아니라_완료로_답한다() {
        val payment = stored(cancelledPayment(id = 3L, orderId = 300L))

        handler.handle(request("cancel-300-1", 300L))

        assertThat(payment.status).isEqualTo(PaymentStatus.CANCELLED)
        verify(transactionalEventPublisher, times(1)).publishEvent(
            eq("Payment"), eq("3"), eq("PaymentCancellationCompleted"), any(), any(), eq("300")
        )
        verify(transactionalEventPublisher, never()).publishEvent(
            any(), any(), eq("PaymentCancellationFailed"), any(), any(), anyOrNull()
        )
        verify(paymentEventPublisher, never()).publishPaymentCancelledEvent(any())
        verify(couponCommandService, never()).restoreCouponByOrder(any())
        assertThat(logs["cancel-300-1"]!!.status).isEqualTo(CompensationEventLogStatus.COMPLETED)
    }

    @Test
    fun 이미_취소된_결제의_내부_동기_취소는_실패_없이_취소된_결제를_돌려주고_되돌리기를_다시_하지_않는다() {
        stored(cancelledPayment(id = 4L, orderId = 400L))

        val result = service.cancelPaymentInternal(4L)

        assertThat(result.status).isEqualTo(PaymentStatus.CANCELLED)
        verify(paymentRepository, never()).save(any<Payment>())
        verify(paymentEventPublisher, never()).publishPaymentCancelledEvent(any())
        verify(couponCommandService, never()).restoreCouponByOrder(any())
        verify(paymentMetrics, never()).recordPaymentCancelled()
    }

    @Test
    fun 같은_결제에_취소_요청이_두_번_와도_둘_다_완료이고_포인트_회수와_쿠폰_복원은_한_번만_일어난다() {
        val payment = stored(succeededPayment(id = 5L, orderId = 500L, couponId = 7L))

        handler.handle(request("cancel-500-1", 500L))
        handler.handle(request("cancel-500-2", 500L))

        assertThat(payment.status).isEqualTo(PaymentStatus.CANCELLED)
        verify(transactionalEventPublisher, times(2)).publishEvent(
            eq("Payment"), eq("5"), eq("PaymentCancellationCompleted"), any(), any(), eq("500")
        )
        verify(transactionalEventPublisher, never()).publishEvent(
            any(), any(), eq("PaymentCancellationFailed"), any(), any(), anyOrNull()
        )
        verify(paymentEventPublisher, times(1)).publishPaymentCancelledEvent(any())
        verify(couponCommandService, times(1)).restoreCouponByOrder(500L)
    }

    @Test
    fun 동기_취소_뒤에_취소_요청_이벤트가_와도_완료이고_포인트_회수와_쿠폰_복원은_한_번만_일어난다() {
        val payment = stored(succeededPayment(id = 6L, orderId = 600L, couponId = 8L))

        service.cancelPaymentInternal(6L)
        handler.handle(request("cancel-600-1", 600L))

        assertThat(payment.status).isEqualTo(PaymentStatus.CANCELLED)
        verify(transactionalEventPublisher, times(1)).publishEvent(
            eq("Payment"), eq("6"), eq("PaymentCancellationCompleted"), any(), any(), eq("600")
        )
        verify(transactionalEventPublisher, never()).publishEvent(
            any(), any(), eq("PaymentCancellationFailed"), any(), any(), anyOrNull()
        )
        verify(paymentEventPublisher, times(1)).publishPaymentCancelledEvent(any())
        verify(couponCommandService, times(1)).restoreCouponByOrder(600L)
    }

    private fun stored(payment: Payment): Payment {
        Mockito.lenient().`when`(paymentRepository.findByIdForUpdate(payment.id!!)).thenReturn(payment)
        Mockito.lenient().`when`(paymentRepository.findByOrderId(payment.orderId)).thenReturn(payment)
        return payment
    }

    private fun request(eventId: String, orderId: Long) = objectMapper.createObjectNode().apply {
        put("eventType", "PaymentCancellationRequested")
        put("eventId", eventId)
        put("orderId", orderId)
    }

    private fun pendingPayment(id: Long, orderId: Long, couponId: Long? = null): Payment {
        val payment = Payment.create(
            orderId = orderId,
            userId = 10L,
            amount = BigDecimal("50000"),
            method = PaymentMethod.CREDIT_CARD,
            couponId = couponId,
            couponDiscountAmount = if (couponId != null) BigDecimal("1000") else BigDecimal.ZERO
        )
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(payment, id)
        return payment
    }

    private fun succeededPayment(id: Long, orderId: Long, couponId: Long? = null): Payment =
        pendingPayment(id, orderId, couponId).apply {
            updateStatus(PaymentStatus.SUCCESS, transactionId = "tx-$id", completedAt = LocalDateTime.now())
        }

    private fun cancelledPayment(id: Long, orderId: Long): Payment =
        succeededPayment(id, orderId).apply {
            updateStatus(PaymentStatus.CANCELLED, transactionId = transactionId, completedAt = completedAt)
        }
}
