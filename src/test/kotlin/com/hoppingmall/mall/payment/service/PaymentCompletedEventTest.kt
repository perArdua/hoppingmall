package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.dto.event.PaymentCompletedEvent
import com.hoppingmall.mall.payment.enum.PaymentMethod
import com.hoppingmall.mall.payment.enum.PaymentStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("PaymentCompletedEvent")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PaymentCompletedEventTest {

    @Nested
    @DisplayName("PaymentCompletedEvent 생성")
    inner class PaymentCompletedEventCreation {
        
        @Test
        fun 모든_필드를_지정하여_PaymentCompletedEvent를_생성하면_정상적으로_생성된다() {
            // given
            val completedAt = LocalDateTime.now()

            // when
            val paymentEvent = PaymentCompletedEvent(
                paymentId = 1L,
                orderId = 123L,
                userId = 456L,
                amount = BigDecimal("50000"),
                pointAmount = BigDecimal("1000"),
                method = PaymentMethod.CREDIT_CARD,
                status = PaymentStatus.SUCCESS,
                transactionId = "TXN_123",
                completedAt = completedAt
            )

            // then
            assertEquals(1L, paymentEvent.paymentId)
            assertEquals(123L, paymentEvent.orderId)
            assertEquals(456L, paymentEvent.userId)
            assertEquals(BigDecimal("50000"), paymentEvent.amount)
            assertEquals(BigDecimal("1000"), paymentEvent.pointAmount)
            assertEquals(PaymentMethod.CREDIT_CARD, paymentEvent.method)
            assertEquals(PaymentStatus.SUCCESS, paymentEvent.status)
            assertEquals("TXN_123", paymentEvent.transactionId)
            assertEquals(completedAt, paymentEvent.completedAt)
        }
    }
    
    @Nested
    @DisplayName("equals 및 hashCode")
    inner class EqualsAndHashCode {
        
        @Test
        fun 동일한_데이터로_생성된_PaymentCompletedEvent는_equals와_hashCode가_동일하다() {
            // given
            val completedAt = LocalDateTime.of(2024, 1, 1, 0, 0)
            val paymentEvent1 = PaymentCompletedEvent(
                paymentId = 1L,
                orderId = 123L,
                userId = 456L,
                amount = BigDecimal("50000"),
                pointAmount = BigDecimal("1000"),
                method = PaymentMethod.CREDIT_CARD,
                status = PaymentStatus.SUCCESS,
                transactionId = "TXN_123",
                completedAt = completedAt
            )

            val paymentEvent2 = PaymentCompletedEvent(
                paymentId = 1L,
                orderId = 123L,
                userId = 456L,
                amount = BigDecimal("50000"),
                pointAmount = BigDecimal("1000"),
                method = PaymentMethod.CREDIT_CARD,
                status = PaymentStatus.SUCCESS,
                transactionId = "TXN_123",
                completedAt = completedAt
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
        fun PaymentCompletedEvent의_toString은_모든_필드_정보를_포함한다() {
            // given
            val paymentEvent = PaymentCompletedEvent(
                paymentId = 1L,
                orderId = 123L,
                userId = 456L,
                amount = BigDecimal("50000"),
                pointAmount = BigDecimal("1000"),
                method = PaymentMethod.BANK_TRANSFER,
                status = PaymentStatus.SUCCESS,
                transactionId = "TXN_123",
                completedAt = LocalDateTime.of(2024, 1, 1, 0, 0)
            )

            // when
            val toString = paymentEvent.toString()
            
            // then
            assertTrue(toString.contains("123"))
            assertTrue(toString.contains("456"))
            assertTrue(toString.contains("50000"))
            assertTrue(toString.contains("BANK_TRANSFER"))
        }
    }
}
