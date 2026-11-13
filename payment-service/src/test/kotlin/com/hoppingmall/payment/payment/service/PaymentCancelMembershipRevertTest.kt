package com.hoppingmall.payment.payment.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.payment.config.PaymentMetrics
import com.hoppingmall.payment.coupon.service.CouponCommandService
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.point.service.strategy.PointEarnRateStrategy
import com.hoppingmall.payment.support.FakeOrderSummaryQueryPort
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
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("결제 취소의 멤버십 되돌리기 요청")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentCancelMembershipRevertTest {

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

    private fun commandService() = PaymentCommandServiceImpl(
        paymentRepository,
        paymentService,
        PaymentEventService(KafkaPaymentEventPublisher(transactionalEventPublisher)),
        paymentNotificationService,
        couponCommandService,
        paymentMetrics,
        pointEarnRateStrategy,
        FakeOrderSummaryQueryPort(),
        transactionManager
    )

    private fun successPayment(amount: BigDecimal): Payment {
        val payment = Payment.create(
            orderId = 100L,
            userId = 10L,
            amount = amount,
            method = PaymentMethod.CREDIT_CARD
        )
        payment.updateStatus(
            newStatus = PaymentStatus.SUCCESS,
            transactionId = "txn-membership-1",
            completedAt = LocalDateTime.of(2026, 11, 13, 10, 0, 0)
        )
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(payment, 1L)
        return payment
    }

    @Test
    fun 완료된_결제를_취소하면_결제_금액_전액의_멤버십_되돌리기_요청이_갱신과_같은_토픽_같은_키로_outbox에_쌓인다() {
        val payment = successPayment(BigDecimal("89000"))
        whenever(paymentRepository.findByIdForUpdate(1L)).thenReturn(payment)
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }

        commandService().cancelPaymentInternal(1L)

        val eventTypes = argumentCaptor<String>()
        val eventData = argumentCaptor<Any>()
        verify(transactionalEventPublisher, times(1)).publishEvent(
            eq("Payment"), eq("1"), eventTypes.capture(), eventData.capture(), eq(KafkaTopics.MEMBERSHIP_UPDATE_REQUEST), eq("10")
        )
        assertThat(eventTypes.firstValue).isEqualTo("MembershipRevertRequested")
        val data = eventData.firstValue as Map<*, *>
        assertThat(data["eventId"]).isEqualTo("membership-revert-cancel-1")
        assertThat(data["userId"]).isEqualTo(10L)
        assertThat(data["orderId"]).isEqualTo(100L)
        assertThat(data["paymentId"]).isEqualTo(1L)
        assertThat(data["amount"]).isEqualTo(BigDecimal("89000"))
        assertThat(data["reason"]).isEqualTo("CANCEL")
        assertThat(data["refundId"]).isNull()
    }

    @Test
    fun 금액이_0인_결제는_멤버십에_더하지_않았으므로_취소해도_되돌리기_요청을_내지_않는다() {
        val payment = successPayment(BigDecimal.ZERO)
        whenever(paymentRepository.findByIdForUpdate(1L)).thenReturn(payment)
        whenever(paymentRepository.save(any<Payment>())).thenAnswer { it.arguments[0] }

        commandService().cancelPaymentInternal(1L)

        verify(transactionalEventPublisher, never()).publishEvent(
            any(), any(), any(), any(), eq(KafkaTopics.MEMBERSHIP_UPDATE_REQUEST), anyOrNull()
        )
    }

    @Test
    fun 이미_취소된_결제의_취소_요청은_되돌리기_요청을_다시_내지_않는다() {
        val payment = successPayment(BigDecimal("89000"))
        payment.updateStatus(
            newStatus = PaymentStatus.CANCELLED,
            transactionId = payment.transactionId,
            completedAt = payment.completedAt
        )
        whenever(paymentRepository.findByIdForUpdate(1L)).thenReturn(payment)

        commandService().cancelPaymentInternal(1L)

        verify(transactionalEventPublisher, never()).publishEvent(
            any(), any(), any(), any(), eq(KafkaTopics.MEMBERSHIP_UPDATE_REQUEST), anyOrNull()
        )
    }
}
