package com.hoppingmall.payment.payment.service.strategy

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.BaseEntity
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.payment.payment.domain.CompensationEventLog
import com.hoppingmall.payment.payment.domain.CompensationEventLogStatus
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.CompensationEventLogRepository
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.dto.response.PaymentResponse
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.service.CompensationEventLogService
import com.hoppingmall.payment.payment.service.PaymentCommandService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import java.math.BigDecimal

@DisplayName("결제 취소 요청의 재시도 소진 처리")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentCancellationRetriesExhaustedTest {

    @Mock
    private lateinit var compensationEventLogRepository: CompensationEventLogRepository

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var paymentCommandService: PaymentCommandService

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    private val logs = mutableMapOf<String, CompensationEventLog>()

    private lateinit var handler: PaymentCancellationRequestedHandler

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(compensationEventLogRepository.findByEventId(any())).thenAnswer {
            logs[it.getArgument<String>(0)]
        }
        Mockito.lenient().`when`(compensationEventLogRepository.save(any<CompensationEventLog>())).thenAnswer {
            val log = it.getArgument<CompensationEventLog>(0)
            logs[log.eventId] = log
            log
        }
        handler = PaymentCancellationRequestedHandler(
            CompensationEventLogService(compensationEventLogRepository),
            paymentRepository,
            paymentCommandService,
            transactionalEventPublisher
        )
    }

    @Test
    fun 취소가_커밋된_뒤_완료_이벤트_쓰기가_재시도_내내_실패하면_재시도_소진_처리에서_완료_이벤트를_내고_실패_이벤트는_내지_않는다() {
        val payment = payment(orderId = 100L, id = 1L)
        whenever(paymentRepository.findByOrderId(100L)).thenReturn(payment)
        whenever(paymentCommandService.cancelPaymentInternal(1L)).thenAnswer {
            if (payment.status != PaymentStatus.CANCELLED) {
                payment.updateStatus(PaymentStatus.CANCELLED, transactionId = payment.transactionId)
            }
            mock<PaymentResponse>()
        }
        Mockito.lenient()
            .doThrow(DataAccessResourceFailureException("outbox 쓰기 실패"))
            .doThrow(DataAccessResourceFailureException("outbox 쓰기 실패"))
            .doThrow(DataAccessResourceFailureException("outbox 쓰기 실패"))
            .doThrow(DataAccessResourceFailureException("outbox 쓰기 실패"))
            .doNothing()
            .`when`(transactionalEventPublisher)
            .publishEvent(any(), any(), eq("PaymentCancellationCompleted"), any(), any(), anyOrNull())

        repeat(4) {
            assertThrows<DataAccessResourceFailureException> { handler.handle(request("cancel-100-1", 100L)) }
        }
        handler.onRetriesExhausted(request("cancel-100-1", 100L))

        assertThat(payment.status).isEqualTo(PaymentStatus.CANCELLED)
        verify(transactionalEventPublisher, times(5)).publishEvent(
            eq("Payment"), eq("1"), eq("PaymentCancellationCompleted"), any(), any(), eq("100")
        )
        verify(transactionalEventPublisher, never()).publishEvent(
            any(), any(), eq("PaymentCancellationFailed"), any(), any(), anyOrNull()
        )
        assertThat(logs["cancel-100-1"]!!.status).isEqualTo(CompensationEventLogStatus.COMPLETED)
    }

    @Test
    fun 결제가_취소되지_않은_채_재시도가_소진되면_실패_이벤트를_낸다() {
        val payment = payment(orderId = 200L, id = 2L)
        whenever(paymentRepository.findByOrderId(200L)).thenReturn(payment)
        whenever(paymentCommandService.cancelPaymentInternal(2L))
            .thenThrow(DataAccessResourceFailureException("결제 DB 쓰기 실패"))

        repeat(4) {
            assertThrows<DataAccessResourceFailureException> { handler.handle(request("cancel-200-1", 200L)) }
        }
        handler.onRetriesExhausted(request("cancel-200-1", 200L))

        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS)
        verify(transactionalEventPublisher, times(1)).publishEvent(
            eq("Payment"), eq("200"), eq("PaymentCancellationFailed"), any(), any(), eq("200")
        )
        verify(transactionalEventPublisher, never()).publishEvent(
            any(), any(), eq("PaymentCancellationCompleted"), any(), any(), anyOrNull()
        )
        assertThat(logs["cancel-200-1"]!!.status).isEqualTo(CompensationEventLogStatus.COMPLETED)
    }

    @Test
    fun 결제가_없는_채_재시도가_소진되면_실패_이벤트를_낸다() {
        Mockito.lenient().`when`(paymentRepository.findByOrderId(300L)).thenReturn(null)

        handler.onRetriesExhausted(request("cancel-300-1", 300L))

        verify(transactionalEventPublisher, times(1)).publishEvent(
            eq("Payment"), eq("300"), eq("PaymentCancellationFailed"), any(), any(), eq("300")
        )
        verify(transactionalEventPublisher, never()).publishEvent(
            any(), any(), eq("PaymentCancellationCompleted"), any(), any(), anyOrNull()
        )
        assertThat(logs["cancel-300-1"]!!.status).isEqualTo(CompensationEventLogStatus.COMPLETED)
    }

    @Test
    fun 이미_처리_완료된_요청은_재시도_소진_처리에서_아무_이벤트도_내지_않는다() {
        val payment = payment(orderId = 400L, id = 4L)
        whenever(paymentRepository.findByOrderId(400L)).thenReturn(payment)
        whenever(paymentCommandService.cancelPaymentInternal(4L)).thenAnswer {
            payment.updateStatus(PaymentStatus.CANCELLED, transactionId = payment.transactionId)
            mock<PaymentResponse>()
        }

        handler.handle(request("cancel-400-1", 400L))
        handler.onRetriesExhausted(request("cancel-400-1", 400L))

        verify(transactionalEventPublisher, times(1)).publishEvent(
            eq("Payment"), eq("4"), eq("PaymentCancellationCompleted"), any(), any(), eq("400")
        )
        verify(transactionalEventPublisher, never()).publishEvent(
            any(), any(), eq("PaymentCancellationFailed"), any(), any(), anyOrNull()
        )
    }

    private fun request(eventId: String, orderId: Long) = objectMapper.createObjectNode().apply {
        put("eventType", "PaymentCancellationRequested")
        put("eventId", eventId)
        put("orderId", orderId)
    }

    private fun payment(orderId: Long, id: Long): Payment {
        val payment = Payment.create(
            orderId = orderId,
            userId = 10L,
            amount = BigDecimal("50000"),
            method = PaymentMethod.CREDIT_CARD
        )
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(payment, id)
        payment.updateStatus(PaymentStatus.SUCCESS, transactionId = "tx-$id")
        return payment
    }
}
