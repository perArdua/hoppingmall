package com.hoppingmall.payment.payment.service.strategy

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.CompensationEventLog
import com.hoppingmall.payment.payment.domain.CompensationEventLogStatus
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.dto.response.PaymentResponse
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.exception.PaymentInvalidStateException
import com.hoppingmall.payment.payment.service.CompensationEventLogService
import com.hoppingmall.payment.payment.service.PaymentCommandService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.math.BigDecimal

@DisplayName("PaymentCancellationRequestedHandler")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentCancellationRequestedHandlerTest {

    @Mock
    private lateinit var compensationEventLogService: CompensationEventLogService

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var paymentCommandService: PaymentCommandService

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @InjectMocks
    private lateinit var handler: PaymentCancellationRequestedHandler

    private val objectMapper = ObjectMapper()

    @Test
    fun 결제_취소_성공_시_PaymentCancellationCompleted_이벤트를_발행한다() {
        val node = objectMapper.createObjectNode().apply {
            put("eventId", "cancel-100-123")
            put("orderId", 100L)
        }
        val log = CompensationEventLog(
            eventId = "cancel-100-123",
            compensationType = "PAYMENT_CANCELLATION_REQUESTED",
            paymentId = 0L,
            orderId = 100L
        )
        val payment = Payment.create(orderId = 100L, userId = 10L, amount = BigDecimal("50000"), method = PaymentMethod.CREDIT_CARD)
        setEntityId(payment, 1L)

        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any())).thenReturn(log)
        whenever(paymentRepository.findByOrderId(100L)).thenReturn(payment)
        whenever(paymentCommandService.cancelPaymentInternal(1L)).thenReturn(mock())

        handler.handle(node)

        verify(paymentCommandService).cancelPaymentInternal(1L)
        verify(transactionalEventPublisher).publishEvent(
            eq("Payment"), eq("1"), eq("PaymentCancellationCompleted"), any(), any(), eq("100")
        )
        verify(compensationEventLogService).markCompleted("cancel-100-123")
    }

    @Test
    fun 결제_미존재_시_PaymentCancellationFailed_이벤트를_발행한다() {
        val node = objectMapper.createObjectNode().apply {
            put("eventId", "cancel-200-456")
            put("orderId", 200L)
        }
        val log = CompensationEventLog(
            eventId = "cancel-200-456",
            compensationType = "PAYMENT_CANCELLATION_REQUESTED",
            paymentId = 0L,
            orderId = 200L
        )

        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any())).thenReturn(log)
        whenever(paymentRepository.findByOrderId(200L)).thenReturn(null)

        handler.handle(node)

        verify(paymentCommandService, never()).cancelPaymentInternal(any())
        verify(transactionalEventPublisher).publishEvent(
            eq("Payment"), eq("200"), eq("PaymentCancellationFailed"), any(), any(), eq("200")
        )
        verify(compensationEventLogService).markCompleted("cancel-200-456")
    }

    @Test
    fun 결제_상태_부적합_시_PaymentCancellationFailed_이벤트를_발행한다() {
        val node = objectMapper.createObjectNode().apply {
            put("eventId", "cancel-300-789")
            put("orderId", 300L)
        }
        val log = CompensationEventLog(
            eventId = "cancel-300-789",
            compensationType = "PAYMENT_CANCELLATION_REQUESTED",
            paymentId = 0L,
            orderId = 300L
        )
        val payment = Payment.create(orderId = 300L, userId = 10L, amount = BigDecimal("50000"), method = PaymentMethod.CREDIT_CARD)
        setEntityId(payment, 3L)

        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any())).thenReturn(log)
        whenever(paymentRepository.findByOrderId(300L)).thenReturn(payment)
        whenever(paymentCommandService.cancelPaymentInternal(3L)).thenThrow(PaymentInvalidStateException())

        handler.handle(node)

        verify(transactionalEventPublisher).publishEvent(
            eq("Payment"), eq("300"), eq("PaymentCancellationFailed"), any(), any(), eq("300")
        )
        verify(compensationEventLogService).markCompleted("cancel-300-789")
    }

    @Test
    fun 이미_처리된_이벤트는_멱등하게_스킵한다() {
        val node = objectMapper.createObjectNode().apply {
            put("eventId", "cancel-400-000")
            put("orderId", 400L)
        }
        val completedLog = CompensationEventLog(
            eventId = "cancel-400-000",
            compensationType = "PAYMENT_CANCELLATION_REQUESTED",
            paymentId = 0L,
            orderId = 400L
        ).apply { status = CompensationEventLogStatus.COMPLETED }

        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any())).thenReturn(completedLog)

        handler.handle(node)

        verify(paymentCommandService, never()).cancelPaymentInternal(any())
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), anyOrNull())
    }

    private fun setEntityId(entity: Any, id: Long) {
        val idField = com.hoppingmall.common.BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(entity, id)
    }
}
