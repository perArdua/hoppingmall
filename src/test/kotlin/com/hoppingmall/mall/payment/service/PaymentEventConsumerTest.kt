package com.hoppingmall.mall.payment.service

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.slf4j.Logger

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
            val paymentEvent = PaymentEvent(
                orderId = 1L,
                userId = 1L,
                amount = 50000L,
                paymentType = PaymentType.CREDIT_CARD
            )

            // when & then
            paymentEventConsumer.handlePaymentEvent(paymentEvent)
        }

        @Test
        fun 계좌이체_결제_이벤트_처리_요청이_성공하면_정상적으로_처리된다() {
            // given
            val paymentEvent = PaymentEvent(
                orderId = 2L,
                userId = 2L,
                amount = 30000L,
                paymentType = PaymentType.BANK_TRANSFER
            )

            // when & then
            paymentEventConsumer.handlePaymentEvent(paymentEvent)
        }

        @Test
        fun 가상계좌_결제_이벤트_처리_요청이_성공하면_정상적으로_처리된다() {
            // given
            val paymentEvent = PaymentEvent(
                orderId = 3L,
                userId = 3L,
                amount = 70000L,
                paymentType = PaymentType.VIRTUAL_ACCOUNT
            )

            // when & then
            paymentEventConsumer.handlePaymentEvent(paymentEvent)
        }

        @Test
        fun 대금액_신용카드_결제_이벤트_처리_시_예외가_발생한다() {
            // given
            val paymentEvent = PaymentEvent(
                orderId = 4L,
                userId = 4L,
                amount = 2000000L,
                paymentType = PaymentType.CREDIT_CARD
            )

            // when & then
            assertThrows<RuntimeException> {
                paymentEventConsumer.handlePaymentEvent(paymentEvent)
            }
        }
    }
}