package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.payment.payment.domain.CompensationEventLog
import com.hoppingmall.payment.payment.domain.CompensationEventLogStatus
import com.hoppingmall.payment.payment.exception.PaymentCancelInProgressException
import com.hoppingmall.payment.payment.exception.PaymentInvalidStateException
import com.hoppingmall.payment.payment.service.strategy.CompensationEventHandlerRegistry
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("결제 역보상의 결제 취소")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentReversalCancelTest {

    @Mock
    private lateinit var compensationEventLogService: CompensationEventLogService

    @Mock
    private lateinit var refundPointsService: RefundPointsService

    @Mock
    private lateinit var paymentCommandService: PaymentCommandService

    @Mock
    private lateinit var compensationEventHandlerRegistry: CompensationEventHandlerRegistry

    @Spy
    private var objectMapper: ObjectMapper = ObjectMapper()

    @InjectMocks
    private lateinit var consumer: PaymentCompensationConsumer

    private val message = """
        {"eventType":"PaymentReversalRequested","eventId":"reversal-tx-742","orderId":200,"paymentId":100,"userId":1,"reason":"RESERVATION_EXPIRED"}
    """.trimIndent()

    private fun reversalLog(status: CompensationEventLogStatus) = CompensationEventLog(
        eventId = "reversal-tx-742",
        compensationType = "PAYMENT_REVERSAL",
        paymentId = 100L,
        orderId = 200L,
        status = status
    )

    @Test
    fun 역보상을_받으면_결제를_취소하고_처리_완료로_기록한다() {
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any()))
            .thenReturn(reversalLog(CompensationEventLogStatus.PENDING))

        consumer.handlePaymentReversal(message)

        verify(paymentCommandService).cancelPaymentInternal(100L)
        verify(compensationEventLogService).markCompleted("reversal-tx-742")
    }

    @Test
    fun 진행_중인_결제의_역보상은_처리_완료로_기록하지_않고_예외를_던져_다시_시도되게_한다() {
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any()))
            .thenReturn(reversalLog(CompensationEventLogStatus.PENDING))
        whenever(paymentCommandService.cancelPaymentInternal(100L)).thenThrow(PaymentCancelInProgressException())

        assertThrows<PaymentCancelInProgressException> { consumer.handlePaymentReversal(message) }

        verify(compensationEventLogService, never()).markCompleted(any())
    }

    @Test
    fun 취소할_수_없는_상태의_결제는_취소_없이_처리_완료로_기록한다() {
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any()))
            .thenReturn(reversalLog(CompensationEventLogStatus.PENDING))
        whenever(paymentCommandService.cancelPaymentInternal(100L)).thenThrow(PaymentInvalidStateException())

        consumer.handlePaymentReversal(message)

        verify(compensationEventLogService).markCompleted("reversal-tx-742")
    }

    @Test
    fun 이미_처리된_역보상은_결제를_다시_취소하지_않는다() {
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any()))
            .thenReturn(reversalLog(CompensationEventLogStatus.COMPLETED))

        consumer.handlePaymentReversal(message)

        verify(paymentCommandService, never()).cancelPaymentInternal(any())
        verify(compensationEventLogService, never()).markCompleted(any())
    }
}
