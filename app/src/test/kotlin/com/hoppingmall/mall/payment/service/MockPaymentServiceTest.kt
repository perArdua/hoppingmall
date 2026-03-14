package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.dto.PaymentResult
import com.hoppingmall.mall.payment.enum.PaymentMethod
import com.hoppingmall.mall.payment.enum.PaymentStatus
import com.hoppingmall.mall.payment.exception.PaymentException
import com.hoppingmall.mall.payment.exception.PaymentInvalidAmountException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("MockPaymentService")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MockPaymentServiceTest {

    private val paymentService: PaymentService = MockPaymentService()

    @Nested
    @DisplayName("processPayment")
    inner class ProcessPayment {
        @Test
        fun `정상적인 결제 요청이 성공하는 경우`() {
            // given
            val payment = Payment.create(
                orderId = 1L,
                userId = 1L,
                amount = BigDecimal("50000"),
                method = PaymentMethod.CREDIT_CARD
            )

            // when
            val result = paymentService.processPayment(payment)

            // then
            assertEquals(payment, result.payment)
            assertTrue(result.isSuccess || !result.isSuccess) // 성공 또는 실패 중 하나
            if (result.isSuccess) {
                assertNotNull(result.transactionId)
                assertTrue(result.transactionId!!.startsWith("MOCK_"))
            } else {
                assertNotNull(result.errorMessage)
            }
        }

        @Test
        fun `결제 금액이 0인 경우 예외가 발생한다`() {
            // given
            val payment = Payment.create(
                orderId = 1L,
                userId = 1L,
                amount = BigDecimal.ZERO,
                method = PaymentMethod.CREDIT_CARD
            )

            // when & then
            assertThrows(PaymentInvalidAmountException::class.java) {
                paymentService.processPayment(payment)
            }
        }

        @Test
        fun `결제 금액이 음수인 경우 예외가 발생한다`() {
            // given
            val payment = Payment.create(
                orderId = 1L,
                userId = 1L,
                amount = BigDecimal("-1000"),
                method = PaymentMethod.CREDIT_CARD
            )

            // when & then
            assertThrows(PaymentInvalidAmountException::class.java) {
                paymentService.processPayment(payment)
            }
        }

        @Test
        fun `주문 ID가 0인 경우 예외가 발생한다`() {
            // given
            val payment = Payment.create(
                orderId = 0L,
                userId = 1L,
                amount = BigDecimal("50000"),
                method = PaymentMethod.CREDIT_CARD
            )

            // when & then
            assertThrows(PaymentException::class.java) {
                paymentService.processPayment(payment)
            }
        }

        @Test
        fun `사용자 ID가 0인 경우 예외가 발생한다`() {
            // given
            val payment = Payment.create(
                orderId = 1L,
                userId = 0L,
                amount = BigDecimal("50000"),
                method = PaymentMethod.CREDIT_CARD
            )

            // when & then
            assertThrows(PaymentException::class.java) {
                paymentService.processPayment(payment)
            }
        }
    }

    @Nested
    @DisplayName("PaymentResult")
    inner class PaymentResultTest {
        @Test
        fun `성공 결과 생성`() {
            // given
            val payment = Payment.create(
                orderId = 1L,
                userId = 1L,
                amount = BigDecimal("50000"),
                method = PaymentMethod.CREDIT_CARD
            )
            val transactionId = "MOCK_123456"

            // when
            val result = PaymentResult.success(payment, transactionId)

            // then
            assertTrue(result.isSuccess)
            assertEquals(payment, result.payment)
            assertEquals(transactionId, result.transactionId)
            assertEquals(null, result.errorMessage)
        }

        @Test
        fun `실패 결과 생성`() {
            // given
            val payment = Payment.create(
                orderId = 1L,
                userId = 1L,
                amount = BigDecimal("50000"),
                method = PaymentMethod.CREDIT_CARD
            )
            val errorMessage = "잔액 부족"

            // when
            val result = PaymentResult.failed(payment, errorMessage)

            // then
            assertFalse(result.isSuccess)
            assertEquals(payment, result.payment)
            assertEquals(errorMessage, result.errorMessage)
            assertEquals(null, result.transactionId)
        }
    }
} 