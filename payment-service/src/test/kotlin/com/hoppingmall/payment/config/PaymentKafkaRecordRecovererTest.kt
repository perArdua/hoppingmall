package com.hoppingmall.payment.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.BaseEntity
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.dlq.service.DLQCommandService
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.payment.payment.domain.CompensationEventLog
import com.hoppingmall.payment.payment.domain.CompensationEventLogStatus
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.domain.repository.CompensationEventLogRepository
import com.hoppingmall.payment.payment.domain.repository.PaymentRepository
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.service.CompensationEventLogService
import com.hoppingmall.payment.payment.service.PaymentCommandService
import com.hoppingmall.payment.payment.service.PaymentCompensationConsumer
import com.hoppingmall.payment.payment.service.RefundPointsService
import com.hoppingmall.payment.payment.service.strategy.CompensationEventHandlerRegistry
import com.hoppingmall.payment.payment.service.strategy.PaymentCancellationRequestedHandler
import org.apache.kafka.clients.consumer.ConsumerRecord
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import java.math.BigDecimal

@DisplayName("결제 Kafka 메시지 회수기")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentKafkaRecordRecovererTest {

    @Mock
    private lateinit var dlqCommandService: DLQCommandService

    @Mock
    private lateinit var compensationEventLogRepository: CompensationEventLogRepository

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var paymentCommandService: PaymentCommandService

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Mock
    private lateinit var refundPointsService: RefundPointsService

    private val logs = mutableMapOf<String, CompensationEventLog>()

    private lateinit var consumer: PaymentCompensationConsumer

    private lateinit var recoverer: PaymentKafkaRecordRecoverer

    private val cancellationRequest =
        """{"eventType":"PaymentCancellationRequested","eventId":"cancel-100-1","orderId":100}"""

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
        val compensationEventLogService = CompensationEventLogService(compensationEventLogRepository)
        val handler = PaymentCancellationRequestedHandler(
            compensationEventLogService,
            paymentRepository,
            paymentCommandService,
            transactionalEventPublisher
        )
        consumer = PaymentCompensationConsumer(
            compensationEventLogService,
            refundPointsService,
            CompensationEventHandlerRegistry(listOf(handler)),
            ObjectMapper()
        )
        recoverer = PaymentKafkaRecordRecoverer(dlqCommandService, consumer)
    }

    @Test
    fun 결제_취소가_예상_밖_예외로_재시도를_모두_소진하면_취소_실패_이벤트를_한_건_낸다() {
        whenever(paymentRepository.findByOrderId(100L)).thenReturn(payment(orderId = 100L, id = 1L))
        whenever(paymentCommandService.cancelPaymentInternal(1L))
            .thenThrow(DataAccessResourceFailureException("저장소 연결 실패"))

        repeat(4) {
            assertThrows<DataAccessResourceFailureException> { consumer.handleCompensationEvent(cancellationRequest) }
        }
        verify(transactionalEventPublisher, never()).publishEvent(any(), any(), any(), any(), any(), anyOrNull())

        recoverer.accept(record(KafkaTopics.PAYMENT_COMPENSATION, cancellationRequest), RuntimeException("재시도 소진"))

        verify(dlqCommandService).saveDLQMessage(any())
        val eventData = argumentCaptor<Any>()
        verify(transactionalEventPublisher, times(1)).publishEvent(
            eq("Payment"),
            eq("100"),
            eq("PaymentCancellationFailed"),
            eventData.capture(),
            eq(KafkaTopics.PAYMENT_COMPENSATION),
            eq("100")
        )
        assertThat(eventData.firstValue as Map<String, Any>)
            .containsEntry("eventType", "PaymentCancellationFailed")
            .containsEntry("eventId", "cancel-failed-cancel-100-1")
            .containsEntry("orderId", 100L)
        assertThat(logs["cancel-100-1"]!!.status).isEqualTo(CompensationEventLogStatus.COMPLETED)
    }

    @Test
    fun 취소_실패를_알린_뒤_같은_요청이_다시_오면_결제_취소를_다시_시도하지_않는다() {
        whenever(paymentRepository.findByOrderId(100L)).thenReturn(payment(orderId = 100L, id = 1L))
        whenever(paymentCommandService.cancelPaymentInternal(1L))
            .thenThrow(DataAccessResourceFailureException("저장소 연결 실패"))
        repeat(4) {
            assertThrows<DataAccessResourceFailureException> { consumer.handleCompensationEvent(cancellationRequest) }
        }
        recoverer.accept(record(KafkaTopics.PAYMENT_COMPENSATION, cancellationRequest), RuntimeException("재시도 소진"))

        consumer.handleCompensationEvent(cancellationRequest)

        verify(paymentCommandService, times(4)).cancelPaymentInternal(1L)
        verify(transactionalEventPublisher, times(1)).publishEvent(any(), any(), any(), any(), any(), anyOrNull())
    }

    @Test
    fun 일시_오류_뒤_다음_시도에서_취소되면_완료_이벤트만_내고_실패_이벤트는_내지_않는다() {
        whenever(paymentRepository.findByOrderId(100L)).thenReturn(payment(orderId = 100L, id = 1L))
        whenever(paymentCommandService.cancelPaymentInternal(1L))
            .thenThrow(DataAccessResourceFailureException("저장소 연결 실패"))
            .thenReturn(mock())

        assertThrows<DataAccessResourceFailureException> { consumer.handleCompensationEvent(cancellationRequest) }
        consumer.handleCompensationEvent(cancellationRequest)

        verify(transactionalEventPublisher, times(1)).publishEvent(
            eq("Payment"), eq("1"), eq("PaymentCancellationCompleted"), any(), any(), eq("100")
        )
        verify(transactionalEventPublisher, never()).publishEvent(
            any(), any(), eq("PaymentCancellationFailed"), any(), any(), anyOrNull()
        )
    }

    @Test
    fun 이미_완료된_취소_요청이_회수되면_실패_이벤트를_내지_않는다() {
        logs["cancel-100-1"] = CompensationEventLog(
            eventId = "cancel-100-1",
            compensationType = "PAYMENT_CANCELLATION_REQUESTED",
            paymentId = 0L,
            orderId = 100L,
            status = CompensationEventLogStatus.COMPLETED
        )

        recoverer.accept(record(KafkaTopics.PAYMENT_COMPENSATION, cancellationRequest), RuntimeException("재시도 소진"))

        verify(dlqCommandService).saveDLQMessage(any())
        verifyNoInteractions(transactionalEventPublisher)
    }

    @Test
    fun 다른_토픽의_메시지가_회수되면_DLQ에만_저장한다() {
        recoverer.accept(record(KafkaTopics.PAYMENT_REVERSAL, cancellationRequest), RuntimeException("재시도 소진"))

        verify(dlqCommandService).saveDLQMessage(any())
        verifyNoInteractions(transactionalEventPublisher, paymentRepository, compensationEventLogRepository)
    }

    @Test
    fun 읽을_수_없는_보상_메시지가_회수되면_DLQ에만_저장한다() {
        recoverer.accept(record(KafkaTopics.PAYMENT_COMPENSATION, "not-json"), RuntimeException("역직렬화 실패"))
        recoverer.accept(record(KafkaTopics.PAYMENT_COMPENSATION, null), RuntimeException("역직렬화 실패"))

        verify(dlqCommandService, times(2)).saveDLQMessage(any())
        verifyNoInteractions(transactionalEventPublisher, paymentRepository, compensationEventLogRepository)
    }

    private fun record(topic: String, value: String?): ConsumerRecord<String, String?> =
        ConsumerRecord(topic, 0, 7L, "100", value)

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
        return payment
    }
}
