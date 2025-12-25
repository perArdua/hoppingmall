package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.mall.payment.enum.PaymentMethod
import com.hoppingmall.mall.payment.enum.PaymentStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("PaymentEventConsumer")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PaymentEventConsumerTest {

    private val paymentEventConsumer = PaymentEventConsumer()

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

            // when & then
            paymentEventConsumer.handlePaymentEvent(paymentEvent)
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

            // when & then
            paymentEventConsumer.handlePaymentEvent(paymentEvent)
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
    }
}
