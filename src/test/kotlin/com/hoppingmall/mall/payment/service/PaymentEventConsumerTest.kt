package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.mall.payment.enum.PaymentMethod
import com.hoppingmall.mall.payment.enum.PaymentStatus
import com.hoppingmall.mall.payment.domain.repository.PaymentEventLogRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import com.hoppingmall.mall.payment.domain.PaymentEventLog
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("PaymentEventConsumer")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PaymentEventConsumerTest {

    private val paymentEventLogRepository: PaymentEventLogRepository = mock()
    private val paymentEventConsumer = PaymentEventConsumer(paymentEventLogRepository)

    @Nested
    @DisplayName("handlePaymentEvent")
    inner class HandlePaymentEvent {
        
        @Test
        fun 신용카드_결제_이벤트_처리_요청이_성공하면_정상적으로_처리된다() {
            // given
            val paymentEvent = PaymentCompletedEvent(
                paymentId = 1L,
                orderId = 1L,
                userId = 1L,
                amount = BigDecimal("50000"),
                pointAmount = BigDecimal("1000"),
                method = PaymentMethod.CREDIT_CARD,
                status = PaymentStatus.SUCCESS,
                transactionId = "TXN_1",
                completedAt = LocalDateTime.now()
            )

            // when
            paymentEventConsumer.handlePaymentEvent(paymentEvent)

            // then
            val captor = argumentCaptor<PaymentEventLog>()
            verify(paymentEventLogRepository).save(captor.capture())
            assertEquals("TXN_1", captor.firstValue.transactionId)
            assertEquals(1L, captor.firstValue.paymentId)
            assertEquals(1L, captor.firstValue.orderId)
        }

        @Test
        fun 계좌이체_결제_이벤트_처리_요청이_성공하면_정상적으로_처리된다() {
            // given
            val paymentEvent = PaymentCompletedEvent(
                paymentId = 2L,
                orderId = 2L,
                userId = 2L,
                amount = BigDecimal("30000"),
                pointAmount = BigDecimal.ZERO,
                method = PaymentMethod.BANK_TRANSFER,
                status = PaymentStatus.SUCCESS,
                transactionId = "TXN_2",
                completedAt = LocalDateTime.now()
            )

            // when
            paymentEventConsumer.handlePaymentEvent(paymentEvent)

            // then
            val captor = argumentCaptor<PaymentEventLog>()
            verify(paymentEventLogRepository).save(captor.capture())
            assertEquals("TXN_2", captor.firstValue.transactionId)
            assertEquals(2L, captor.firstValue.paymentId)
            assertEquals(2L, captor.firstValue.orderId)
        }

        @Test
        fun 대금액_신용카드_결제_이벤트_처리_시_예외가_발생한다() {
            // given
            val paymentEvent = PaymentCompletedEvent(
                paymentId = 4L,
                orderId = 4L,
                userId = 4L,
                amount = BigDecimal("2000000"),
                pointAmount = BigDecimal.ZERO,
                method = PaymentMethod.CREDIT_CARD,
                status = PaymentStatus.SUCCESS,
                transactionId = "TXN_4",
                completedAt = LocalDateTime.now()
            )

            // when & then
            assertThrows<RuntimeException> {
                paymentEventConsumer.handlePaymentEvent(paymentEvent)
            }
        }

        @Test
        fun 중복_결제_이벤트는_처리하지_않는다() {
            val paymentEvent = PaymentCompletedEvent(
                paymentId = 5L,
                orderId = 5L,
                userId = 5L,
                amount = BigDecimal("30000"),
                pointAmount = BigDecimal.ZERO,
                method = PaymentMethod.BANK_TRANSFER,
                status = PaymentStatus.SUCCESS,
                transactionId = "TXN_DUP",
                completedAt = LocalDateTime.now()
            )

            whenever(paymentEventLogRepository.existsByTransactionId(paymentEvent.transactionId)).thenReturn(true)

            paymentEventConsumer.handlePaymentEvent(paymentEvent)

            verify(paymentEventLogRepository, never()).save(any())
        }
    }
}
