package com.hoppingmall.mall.payment.controller

import com.hoppingmall.mall.payment.dto.request.PaymentRequest
import com.hoppingmall.mall.payment.dto.response.PaymentResponse
import com.hoppingmall.mall.payment.enum.PaymentMethod
import com.hoppingmall.mall.payment.enum.PaymentStatus
import com.hoppingmall.mall.payment.service.PaymentCommandService
import com.hoppingmall.mall.payment.service.PaymentQueryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import com.hoppingmall.mall.global.auth.UserPrincipal
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("PaymentController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class PaymentControllerTest {

    private val paymentCommandService: PaymentCommandService = mock()
    private val paymentQueryService: PaymentQueryService = mock()
    private val paymentController = PaymentController(paymentCommandService, paymentQueryService)

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
            val response = PaymentResponse(
                id = 1L,
                orderId = 1L,
                userId = userId,
                amount = BigDecimal("50000"),
                pointAmount = BigDecimal("1000"),
                couponId = null,
                couponDiscountAmount = BigDecimal.ZERO,
                method = PaymentMethod.CREDIT_CARD.name,
                status = PaymentStatus.SUCCESS,
                transactionId = "TXN_123456",
                errorMessage = null,
                completedAt = LocalDateTime.now(),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
            val principal = UserPrincipal(userId, "test@example.com", "BUYER")

            whenever(paymentCommandService.processPayment(request, userId)).thenReturn(response)

            // when
            val result = paymentController.processPayment(request, principal)

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(response, result.body)
            verify(paymentCommandService).processPayment(request, userId)
        }
    }

    @Nested
    @DisplayName("getPaymentById")
    inner class GetPaymentById {
        @Test
        fun `결제 조회 성공`() {
            // given
            val paymentId = 1L
            val userId = 1L
            val response = PaymentResponse(
                id = paymentId,
                orderId = 1L,
                userId = userId,
                amount = BigDecimal("50000"),
                pointAmount = BigDecimal.ZERO,
                couponId = null,
                couponDiscountAmount = BigDecimal.ZERO,
                method = PaymentMethod.CREDIT_CARD.name,
                status = PaymentStatus.SUCCESS,
                transactionId = "TXN_123456",
                errorMessage = null,
                completedAt = LocalDateTime.now(),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
            val principal = UserPrincipal(userId, "test@example.com", "BUYER")

            whenever(paymentQueryService.getPaymentById(paymentId, userId)).thenReturn(response)

            // when
            val result = paymentController.getPaymentById(paymentId, principal)

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(response, result.body)
            verify(paymentQueryService).getPaymentById(paymentId, userId)
        }
    }

    @Nested
    @DisplayName("getMyPayments")
    inner class GetMyPayments {
        @Test
        fun `내 결제 목록 조회 성공`() {
            // given
            val userId = 1L
            val page = 0
            val size = 10
            val pageable = PageRequest.of(page, size)
            val principal = UserPrincipal(userId, "test@example.com", "BUYER")

            val paymentResponse = PaymentResponse(
                id = 1L,
                orderId = 1L,
                userId = userId,
                amount = BigDecimal("50000"),
                pointAmount = BigDecimal.ZERO,
                couponId = null,
                couponDiscountAmount = BigDecimal.ZERO,
                method = PaymentMethod.CREDIT_CARD.name,
                status = PaymentStatus.SUCCESS,
                transactionId = "TXN_123456",
                errorMessage = null,
                completedAt = LocalDateTime.now(),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            val payments = listOf(paymentResponse)
            val pageResponse = PageImpl(payments, pageable, 1)

            whenever(paymentQueryService.getPaymentsByUserId(userId, pageable)).thenReturn(pageResponse)

            // when
            val result = paymentController.getMyPayments(principal, page, size)

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(pageResponse, result.body)
            verify(paymentQueryService).getPaymentsByUserId(userId, pageable)
        }
    }

    @Nested
    @DisplayName("getPaymentsByOrderId")
    inner class GetPaymentsByOrderId {
        @Test
        fun `주문별 결제 목록 조회 성공`() {
            // given
            val orderId = 1L
            val userId = 1L

            val paymentResponse = PaymentResponse(
                id = 1L,
                orderId = orderId,
                userId = userId,
                amount = BigDecimal("50000"),
                pointAmount = BigDecimal.ZERO,
                couponId = null,
                couponDiscountAmount = BigDecimal.ZERO,
                method = PaymentMethod.CREDIT_CARD.name,
                status = PaymentStatus.SUCCESS,
                transactionId = "TXN_123456",
                errorMessage = null,
                completedAt = LocalDateTime.now(),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            val payments = listOf(paymentResponse)
            val principal = UserPrincipal(userId, "test@example.com", "BUYER")

            whenever(paymentQueryService.getPaymentsByOrderId(orderId, userId)).thenReturn(payments)

            // when
            val result = paymentController.getPaymentsByOrderId(orderId, principal)

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(payments, result.body)
            verify(paymentQueryService).getPaymentsByOrderId(orderId, userId)
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
            val response = PaymentResponse(
                id = paymentId,
                orderId = 1L,
                userId = userId,
                amount = BigDecimal("50000"),
                pointAmount = BigDecimal("1000"),
                couponId = null,
                couponDiscountAmount = BigDecimal.ZERO,
                method = PaymentMethod.CREDIT_CARD.name,
                status = PaymentStatus.CANCELLED,
                transactionId = "TXN_123456",
                errorMessage = null,
                completedAt = LocalDateTime.now(),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
            val principal = UserPrincipal(userId, "test@example.com", "BUYER")

            whenever(paymentCommandService.cancelPayment(paymentId, userId)).thenReturn(response)

            // when
            val result = paymentController.cancelPayment(paymentId, principal)

            // then
            assertEquals(HttpStatus.OK, result.statusCode)
            assertEquals(response, result.body)
            verify(paymentCommandService).cancelPayment(paymentId, userId)
        }
    }
} 