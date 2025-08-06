package com.hoppingmall.mall.payment.domain

import com.hoppingmall.mall.payment.enum.PaymentMethod
import com.hoppingmall.mall.payment.enum.PaymentStatus
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.fixture.failedFixture
import com.hoppingmall.mall.support.fixture.successFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("Payment")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PaymentTest {

    @Nested
    @DisplayName("결제_생성")
    inner class Create {
        @Test
        fun `신용카드_결제_생성_성공`() {
            // given
            val orderId = 1L
            val userId = 1L
            val amount = BigDecimal("50000")
            val method = PaymentMethod.CREDIT_CARD
            val pointAmount = BigDecimal("1000")

            // when
            val payment = Payment.create(
                orderId = orderId,
                userId = userId,
                amount = amount,
                method = method,
                pointAmount = pointAmount
            )

            // then
            assertEquals(orderId, payment.orderId)
            assertEquals(userId, payment.userId)
            assertEquals(amount, payment.amount)
            assertEquals(method, payment.method)
            assertEquals(pointAmount, payment.pointAmount)
            assertEquals(PaymentStatus.PENDING, payment.status)
            assertEquals(null, payment.transactionId)
            assertEquals(null, payment.completedAt)
            assertEquals(null, payment.errorMessage)
        }

        @Test
        fun `계좌이체_결제_생성_성공`() {
            // given
            val orderId = 1L
            val userId = 1L
            val amount = BigDecimal("30000")

            // when
            val payment = Payment.create(
                orderId = orderId,
                userId = userId,
                amount = amount,
                method = PaymentMethod.BANK_TRANSFER
            )

            // then
            assertEquals(BigDecimal.ZERO, payment.pointAmount)
            assertEquals(PaymentStatus.PENDING, payment.status)
        }
    }

    @Nested
    @DisplayName("결제_상태_업데이트")
    inner class UpdateStatus {
        @Test
        fun `결제_성공_상태로_업데이트`() {
            // given
            val payment = Payment.fixture()
            val transactionId = "TXN_123456"
            val completedAt = LocalDateTime.now()

            // when
            payment.updateStatus(
                status = PaymentStatus.SUCCESS,
                transactionId = transactionId,
                completedAt = completedAt
            )

            // then
            assertEquals(PaymentStatus.SUCCESS, payment.status)
            assertEquals(transactionId, payment.transactionId)
            assertEquals(completedAt, payment.completedAt)
            assertEquals(null, payment.errorMessage)
        }

        @Test
        fun `결제_실패_상태로_업데이트`() {
            // given
            val payment = Payment.fixture()
            val errorMessage = "잔액 부족"

            // when
            payment.updateStatus(
                status = PaymentStatus.FAILED,
                errorMessage = errorMessage
            )

            // then
            assertEquals(PaymentStatus.FAILED, payment.status)
            assertEquals(errorMessage, payment.errorMessage)
            assertEquals(null, payment.transactionId)
            assertEquals(null, payment.completedAt)
        }

        @Test
        fun `결제_취소_상태로_업데이트`() {
            // given
            val payment = Payment.successFixture()
            val errorMessage = "고객 요청으로 취소"

            // when
            payment.updateStatus(
                status = PaymentStatus.CANCELLED,
                errorMessage = errorMessage
            )

            // then
            assertEquals(PaymentStatus.CANCELLED, payment.status)
            assertEquals(errorMessage, payment.errorMessage)
        }
    }

    @Nested
    @DisplayName("결제_성공_확인")
    inner class IsSuccess {
        @Test
        fun `결제_성공_상태일_때_true_반환`() {
            // given
            val payment = Payment.successFixture()

            // when & then
            assertTrue(payment.isSuccess())
        }

        @Test
        fun `결제_실패_상태일_때_false_반환`() {
            // given
            val payment = Payment.failedFixture()

            // when & then
            assertFalse(payment.isSuccess())
        }

        @Test
        fun `결제_대기_상태일_때_false_반환`() {
            // given
            val payment = Payment.fixture()

            // when & then
            assertFalse(payment.isSuccess())
        }

        @Test
        fun `결제_취소_상태일_때_false_반환`() {
            // given
            val payment = Payment.fixture().apply {
                updateStatus(PaymentStatus.CANCELLED, errorMessage = "취소됨")
            }

            // when & then
            assertFalse(payment.isSuccess())
        }
    }

    @Nested
    @DisplayName("결제_실패_확인")
    inner class IsFailed {
        @Test
        fun `결제_실패_상태일_때_true_반환`() {
            // given
            val payment = Payment.failedFixture()

            // when & then
            assertTrue(payment.isFailed())
        }

        @Test
        fun `결제_성공_상태일_때_false_반환`() {
            // given
            val payment = Payment.successFixture()

            // when & then
            assertFalse(payment.isFailed())
        }

        @Test
        fun `결제_대기_상태일_때_false_반환`() {
            // given
            val payment = Payment.fixture()

            // when & then
            assertFalse(payment.isFailed())
        }
    }

    @Nested
    @DisplayName("결제_복사")
    inner class Copy {
        @Test
        fun `결제_객체_복사_성공`() {
            // given
            val originalPayment = Payment.successFixture()
            val transactionId = "TXN_123456"
            originalPayment.updateStatus(
                status = PaymentStatus.SUCCESS,
                transactionId = transactionId,
                completedAt = LocalDateTime.now()
            )

            // when
            val copiedPayment = originalPayment.copy()

            // then
            assertEquals(originalPayment.orderId, copiedPayment.orderId)
            assertEquals(originalPayment.userId, copiedPayment.userId)
            assertEquals(originalPayment.amount, copiedPayment.amount)
            assertEquals(originalPayment.method, copiedPayment.method)
            assertEquals(originalPayment.pointAmount, copiedPayment.pointAmount)
            assertEquals(originalPayment.status, copiedPayment.status)
            assertEquals(originalPayment.transactionId, copiedPayment.transactionId)
            assertEquals(originalPayment.completedAt, copiedPayment.completedAt)
            assertEquals(originalPayment.errorMessage, copiedPayment.errorMessage)
        }
    }
} 