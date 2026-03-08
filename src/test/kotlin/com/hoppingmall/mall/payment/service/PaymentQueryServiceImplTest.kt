package com.hoppingmall.mall.payment.service

import com.hoppingmall.mall.payment.domain.Payment
import com.hoppingmall.mall.payment.domain.repository.PaymentRepository
import com.hoppingmall.mall.payment.dto.response.PaymentResponse
import com.hoppingmall.mall.payment.enum.PaymentMethod
import com.hoppingmall.mall.payment.enum.PaymentStatus
import com.hoppingmall.mall.payment.exception.PaymentAccessDeniedException
import com.hoppingmall.mall.payment.exception.PaymentNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.fixture.successFixture
import com.hoppingmall.mall.support.fixture.failedFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("PaymentQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PaymentQueryServiceImplTest {

    private val paymentRepository: PaymentRepository = mock()
    private val paymentQueryService = PaymentQueryServiceImpl(paymentRepository)

    @Nested
    @DisplayName("getPaymentById")
    inner class GetPaymentById {
        @Test
        fun `결제 조회 성공`() {
            // given
            val paymentId = 1L
            val userId = 1L
            val payment = Payment.successFixture()

            whenever(paymentRepository.findById(paymentId)).thenReturn(java.util.Optional.of(payment))

            // when
            val result = paymentQueryService.getPaymentById(paymentId, userId)

            // then
            assertEquals(paymentId, result.id)
            assertEquals(userId, result.userId)
            assertEquals(BigDecimal("50000"), result.amount)
            assertEquals(PaymentStatus.SUCCESS, result.status)
        }

        @Test
        fun `존재하지 않는 결제 조회 시 예외 발생`() {
            // given
            val paymentId = 999L

            whenever(paymentRepository.findById(paymentId)).thenReturn(java.util.Optional.empty())

            // when & then
            assertThrows(PaymentNotFoundException::class.java) {
                paymentQueryService.getPaymentById(paymentId, 1L)
            }
        }

        @Test
        fun `다른 사용자의 결제 조회 시 접근 거부`() {
            // given
            val paymentId = 1L
            val payment = Payment.successFixture(userId = 1L)

            whenever(paymentRepository.findById(paymentId)).thenReturn(java.util.Optional.of(payment))

            // when & then
            assertThrows(PaymentAccessDeniedException::class.java) {
                paymentQueryService.getPaymentById(paymentId, 999L)
            }
        }
    }

    @Nested
    @DisplayName("getPaymentsByUserId")
    inner class GetPaymentsByUserId {
        @Test
        fun `사용자별 결제 목록 조회 성공`() {
            // given
            val userId = 1L
            val pageable = PageRequest.of(0, 10)
            
            val payment1 = Payment.successFixture(userId = userId, orderId = 1L)
            val payment2 = Payment.successFixture(userId = userId, orderId = 2L, amount = BigDecimal("20000"))
            
            val payments = listOf(payment1, payment2)
            val page = PageImpl(payments, pageable, 2)

            whenever(paymentRepository.findByUserId(userId, pageable)).thenReturn(page)

            // when
            val result = paymentQueryService.getPaymentsByUserId(userId, pageable)

            // then
            assertEquals(2, result.content.size)
            assertEquals(userId, result.content[0].userId)
            assertEquals(userId, result.content[1].userId)
            assertEquals(BigDecimal("50000"), result.content[0].amount)
            assertEquals(BigDecimal("20000"), result.content[1].amount)
        }

        @Test
        fun `사용자별 결제 목록이 없는 경우`() {
            // given
            val userId = 1L
            val pageable = PageRequest.of(0, 10)
            val emptyPage = PageImpl<Payment>(emptyList(), pageable, 0)

            whenever(paymentRepository.findByUserId(userId, pageable)).thenReturn(emptyPage)

            // when
            val result = paymentQueryService.getPaymentsByUserId(userId, pageable)

            // then
            assertEquals(0, result.content.size)
        }
    }

    @Nested
    @DisplayName("getPaymentsByOrderId")
    inner class GetPaymentsByOrderId {
        @Test
        fun `주문별 결제 조회 성공`() {
            // given
            val orderId = 1L
            val userId = 1L
            val payment = Payment.successFixture(orderId = orderId, userId = userId)

            whenever(paymentRepository.findByOrderId(orderId)).thenReturn(payment)

            // when
            val result = paymentQueryService.getPaymentsByOrderId(orderId, userId)

            // then
            assertEquals(1, result.size)
            assertEquals(orderId, result[0].orderId)
            assertEquals(BigDecimal("50000"), result[0].amount)
        }

        @Test
        fun `주문별 결제가 없는 경우`() {
            // given
            val orderId = 999L

            whenever(paymentRepository.findByOrderId(orderId)).thenReturn(null)

            // when
            val result = paymentQueryService.getPaymentsByOrderId(orderId, 1L)

            // then
            assertEquals(0, result.size)
        }

        @Test
        fun `다른 사용자의 주문 결제 조회 시 접근 거부`() {
            // given
            val orderId = 1L
            val payment = Payment.successFixture(orderId = orderId, userId = 1L)

            whenever(paymentRepository.findByOrderId(orderId)).thenReturn(payment)

            // when & then
            assertThrows(PaymentAccessDeniedException::class.java) {
                paymentQueryService.getPaymentsByOrderId(orderId, 999L)
            }
        }
    }

    @Nested
    @DisplayName("getPaymentByTransactionId")
    inner class GetPaymentByTransactionId {
        @Test
        fun `거래 ID로 결제 조회 성공`() {
            // given
            val transactionId = "TXN_123456"
            val payment = Payment.successFixture(transactionId = transactionId)

            whenever(paymentRepository.findByTransactionId(transactionId)).thenReturn(payment)

            // when
            val result = paymentQueryService.getPaymentByTransactionId(transactionId)

            // then
            assertEquals(transactionId, result!!.transactionId)
            assertEquals(BigDecimal("50000"), result.amount)
        }

        @Test
        fun `거래 ID로 결제가 없는 경우`() {
            // given
            val transactionId = "nonexistent_txn"

            whenever(paymentRepository.findByTransactionId(transactionId)).thenReturn(null)

            // when
            val result = paymentQueryService.getPaymentByTransactionId(transactionId)

            // then
            assertEquals(null, result)
        }
    }
} 