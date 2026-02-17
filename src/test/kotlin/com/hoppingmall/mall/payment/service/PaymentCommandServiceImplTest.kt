package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.domain.repository.PaymentRepository
import com.hoppingmall.mall.payment.dto.PaymentResult
import com.hoppingmall.mall.payment.dto.request.PaymentRequest
import com.hoppingmall.mall.payment.enum.PaymentMethod
import com.hoppingmall.mall.payment.enum.PaymentStatus
import com.hoppingmall.mall.payment.exception.PaymentAccessDeniedException
import com.hoppingmall.mall.payment.exception.PaymentInvalidStateException
import com.hoppingmall.mall.payment.exception.PaymentNotFoundException
import com.hoppingmall.mall.support.fixture.failedFixture
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.fixture.successFixture
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.util.*

@DisplayName("PaymentCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PaymentCommandServiceImplTest {

    private val paymentRepository: PaymentRepository = mock()
    private val paymentService: PaymentService = mock()
    private val paymentEventService: PaymentEventService = mock()
    private val paymentCommandService = PaymentCommandServiceImpl(
        paymentRepository, paymentService, paymentEventService
    )

    @Nested
    @DisplayName("processPayment")
    inner class ProcessPayment {
        @Test
        fun `결제 처리 성공`() {
            // given
            val userId = 1L
            val request = PaymentRequest(
                orderId = 1L,
                amount = BigDecimal("50000"),
                method = PaymentMethod.CREDIT_CARD,
                pointAmount = BigDecimal("1000")
            )
            
            val paymentCaptor = argumentCaptor<Payment>()
            val savedPayment = Payment.fixture()
            val paymentResult = PaymentResult.success(savedPayment, "TXN_123456")
            val updatedPayment = Payment.successFixture()

            whenever(paymentRepository.save(paymentCaptor.capture())).thenAnswer {
                paymentCaptor.firstValue.withId(1L)
            }
            whenever(paymentService.processPayment(any())).thenReturn(paymentResult)
            whenever(paymentRepository.save(updatedPayment)).thenReturn(updatedPayment)
            // when
            val response = paymentCommandService.processPayment(request, userId)

            // then
            assertEquals(updatedPayment.id, response.id)
            assertEquals(request.orderId, response.orderId)
            assertEquals(userId, response.userId)
            assertEquals(request.amount, response.amount)
            assertEquals(request.method.name, response.method)
            assertEquals(PaymentStatus.SUCCESS, response.status)
            assertEquals("TXN_123456", response.transactionId)
            verify(paymentEventService).publishPaymentCompletedEvent(any())
            verify(paymentEventService).publishPointEarnRequestEvent(any())
            verify(paymentEventService).publishMembershipUpdateEvent(any())
            verify(paymentEventService).publishPaymentCompletedNotification(any())
        }

        @Test
        fun `결제 실패 시 실패 이벤트 발행`() {
            // given
            val userId = 1L
            val request = PaymentRequest(
                orderId = 1L,
                amount = BigDecimal("50000"),
                method = PaymentMethod.CREDIT_CARD
            )

            val paymentCaptor = argumentCaptor<Payment>()
            val savedPayment = Payment.fixture()
            val paymentResult = PaymentResult.failed(savedPayment, "잔액 부족")
            val updatedPayment = Payment.failedFixture()

            whenever(paymentRepository.save(paymentCaptor.capture())).thenAnswer {
                paymentCaptor.firstValue.withId(1L)
            }
            whenever(paymentService.processPayment(any())).thenReturn(paymentResult)
            whenever(paymentRepository.save(updatedPayment)).thenReturn(updatedPayment)

            // when
            val response = paymentCommandService.processPayment(request, userId)

            // then
            assertEquals(PaymentStatus.FAILED, response.status)
            assertEquals("잔액 부족", response.errorMessage)
            verify(paymentEventService, never()).publishPaymentCompletedEvent(any())
            verify(paymentEventService, never()).publishPointEarnRequestEvent(any())
            verify(paymentEventService, never()).publishMembershipUpdateEvent(any())
            verify(paymentEventService, never()).publishPaymentCompletedNotification(any())
            verify(paymentEventService).publishPaymentFailedEvent(any())
            verify(paymentEventService).publishPaymentFailedNotification(any())
        }
    }

    @Nested
    @DisplayName("cancelPayment")
    inner class CancelPayment {

        @Test
        fun `결제 취소 성공`() {
            // given
            val paymentId = 1L
            val userId = 1L
            val payment = Payment.successFixture(userId = userId)

            whenever(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment))
            whenever(paymentRepository.save(payment)).thenReturn(payment)

            // when
            val response = paymentCommandService.cancelPayment(paymentId, userId)

            // then
            assertEquals(PaymentStatus.CANCELLED, response.status)
            verify(paymentEventService).publishPaymentCancelledEvent(any())
            verify(paymentEventService).publishPaymentCancelledNotification(any())
        }

        @Test
        fun `결제가 존재하지 않으면 예외 발생`() {
            // given
            whenever(paymentRepository.findById(1L)).thenReturn(Optional.empty())

            // when & then
            assertThrows<PaymentNotFoundException> {
                paymentCommandService.cancelPayment(1L, 1L)
            }
        }

        @Test
        fun `본인의 결제가 아니면 예외 발생`() {
            // given
            val payment = Payment.successFixture(userId = 1L)
            whenever(paymentRepository.findById(1L)).thenReturn(Optional.of(payment))

            // when & then
            assertThrows<PaymentAccessDeniedException> {
                paymentCommandService.cancelPayment(1L, 999L)
            }
        }

        @Test
        fun `SUCCESS 상태가 아니면 예외 발생`() {
            // given
            val payment = Payment.failedFixture(userId = 1L)
            whenever(paymentRepository.findById(1L)).thenReturn(Optional.of(payment))

            // when & then
            assertThrows<PaymentInvalidStateException> {
                paymentCommandService.cancelPayment(1L, 1L)
            }
        }
    }
} 
