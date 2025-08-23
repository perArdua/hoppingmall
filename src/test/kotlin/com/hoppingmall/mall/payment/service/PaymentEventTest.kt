package com.hoppingmall.mall.payment.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PaymentEvent")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PaymentEventTest {

    @Nested
    @DisplayName("PaymentEvent 생성")
    inner class PaymentEventCreation {
        
        @Test
        fun 모든_필드를_지정하여_PaymentEvent를_생성하면_정상적으로_생성된다() {
            // given
            val orderId = 123L
            val userId = 456L
            val amount = 50000L
            val paymentType = PaymentType.CREDIT_CARD
            val currentTime = System.currentTimeMillis()

            // when
            val paymentEvent = PaymentEvent(
                orderId = orderId,
                userId = userId,
                amount = amount,
                paymentType = paymentType,
                timestamp = currentTime
            )

            // then
            assertEquals(orderId, paymentEvent.orderId)
            assertEquals(userId, paymentEvent.userId)
            assertEquals(amount, paymentEvent.amount)
            assertEquals(paymentType, paymentEvent.paymentType)
            assertEquals(currentTime, paymentEvent.timestamp)
        }

        @Test
        fun timestamp_기본값으로_PaymentEvent를_생성하면_현재_시간이_설정된다() {
            // given
            val beforeTime = System.currentTimeMillis()
            
            // when
            val paymentEvent = PaymentEvent(
                orderId = 123L,
                userId = 456L,
                amount = 50000L,
                paymentType = PaymentType.BANK_TRANSFER
            )
            
            val afterTime = System.currentTimeMillis()

            // then
            assertTrue(paymentEvent.timestamp >= beforeTime)
            assertTrue(paymentEvent.timestamp <= afterTime)
        }

    }
    
    @Nested
    @DisplayName("equals 및 hashCode")
    inner class EqualsAndHashCode {
        
        @Test
        fun 동일한_데이터로_생성된_PaymentEvent는_equals와_hashCode가_동일하다() {
            // given
            val paymentEvent1 = PaymentEvent(
                orderId = 123L,
                userId = 456L,
                amount = 50000L,
                paymentType = PaymentType.CREDIT_CARD,
                timestamp = 1000L
            )

            val paymentEvent2 = PaymentEvent(
                orderId = 123L,
                userId = 456L,
                amount = 50000L,
                paymentType = PaymentType.CREDIT_CARD,
                timestamp = 1000L
            )

            // when & then
            assertEquals(paymentEvent1, paymentEvent2)
            assertEquals(paymentEvent1.hashCode(), paymentEvent2.hashCode())
        }

    }
    
    @Nested
    @DisplayName("toString")
    inner class ToString {
        
        @Test
        fun PaymentEvent의_toString은_모든_필드_정보를_포함한다() {
            // given
            val paymentEvent = PaymentEvent(
                orderId = 123L,
                userId = 456L,
                amount = 50000L,
                paymentType = PaymentType.VIRTUAL_ACCOUNT,
                timestamp = 1000L
            )

            // when
            val toString = paymentEvent.toString()
            
            // then
            assertTrue(toString.contains("123"))
            assertTrue(toString.contains("456"))
            assertTrue(toString.contains("50000"))
            assertTrue(toString.contains("VIRTUAL_ACCOUNT"))
        }
    }
}