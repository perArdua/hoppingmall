package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.hoppingmall.common.BaseEntity
import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.common.event.AvroJsonDeserializer
import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.repository.OutboxEventRepository
import com.hoppingmall.outbox.service.OutboxEventWriter
import com.hoppingmall.payment.outbox.service.TransactionalEventPublisherImpl
import com.hoppingmall.payment.payment.domain.CompensationEventLog
import com.hoppingmall.payment.payment.domain.CompensationEventLogStatus
import com.hoppingmall.payment.payment.domain.Payment
import com.hoppingmall.payment.payment.dto.event.PaymentCancelledEvent
import com.hoppingmall.payment.payment.enum.PaymentMethod
import com.hoppingmall.payment.payment.enum.PaymentStatus
import com.hoppingmall.payment.payment.service.strategy.CompensationEventHandlerRegistry
import com.hoppingmall.payment.payment.service.strategy.PaymentCancelledCompensationHandler
import com.hoppingmall.payment.payment.service.strategy.PaymentFailedCompensationHandler
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.groups.Tuple.tuple
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("결제 보상 이벤트를 운영 설정 ObjectMapper로 읽기")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentCompensationEventReadTest {

    @Mock
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Mock
    private lateinit var compensationEventLogService: CompensationEventLogService

    @Mock
    private lateinit var refundPointsService: RefundPointsService

    private val registryScope = "payment-compensation-read"
    private val productionObjectMapper: ObjectMapper = loadProductionObjectMapper()
    private val savedOutboxEvents = mutableListOf<OutboxEvent>()
    private val compensationLogs = linkedMapOf<String, CompensationEventLog>()
    private lateinit var consumer: PaymentCompensationConsumer

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer { invocation ->
            (invocation.arguments[0] as OutboxEvent).also { savedOutboxEvents.add(it) }
        }
        Mockito.lenient().`when`(compensationEventLogService.saveIfAbsent(any(), any(), any(), any())).thenAnswer { invocation ->
            val eventId = invocation.arguments[0] as String
            compensationLogs.getOrPut(eventId) {
                CompensationEventLog(
                    eventId = eventId,
                    compensationType = invocation.arguments[1] as String,
                    paymentId = invocation.arguments[2] as Long,
                    orderId = invocation.arguments[3] as Long,
                    status = CompensationEventLogStatus.PENDING
                )
            }
        }
        Mockito.lenient().doAnswer { invocation ->
            compensationLogs[invocation.arguments[0] as String]?.complete()
            null
        }.`when`(compensationEventLogService).markCompleted(any())

        val registry = CompensationEventHandlerRegistry(
            listOf(
                PaymentFailedCompensationHandler(compensationEventLogService, productionObjectMapper),
                PaymentCancelledCompensationHandler(compensationEventLogService, refundPointsService, productionObjectMapper)
            )
        )
        consumer = PaymentCompensationConsumer(compensationEventLogService, refundPointsService, registry, productionObjectMapper)
    }

    @AfterEach
    fun tearDown() {
        MockSchemaRegistry.dropScope(registryScope)
    }

    @Test
    fun 운영_ObjectMapper는_application_yml의_SNAKE_CASE_설정을_그대로_쓴다() {
        assertThat(productionObjectMapper.propertyNamingStrategy)
            .isInstanceOf(PropertyNamingStrategies.SnakeCaseStrategy::class.java)
    }

    @Test
    fun 실제_발행_코드가_만든_결제_취소_이벤트를_읽으면_메시지의_eventId_orderId_paymentId로_보상한다() {
        paymentEventService().publishPaymentCancelledEvent(successPayment(paymentId = 5L, orderId = 6L, userId = 6L))
        assertThat(savedOutboxEvents.single().eventData).isEqualTo(
            "{\"eventId\":\"payment-cancelled-5\",\"paymentId\":5,\"orderId\":6,\"userId\":6,\"amount\":3000.00,\"transactionId\":\"MOCK_1794499236200_2502\"}"
        )

        consumer.handleCompensationEvent(consumedMessage(savedOutboxEvents.single()))

        assertThat(compensationLogs.values)
            .extracting("eventId", "compensationType", "paymentId", "orderId", "status")
            .containsExactly(tuple("payment-cancelled-5", "PAYMENT_CANCELLED", 5L, 6L, CompensationEventLogStatus.COMPLETED))
        verify(refundPointsService).refundPoints(6L, 5L)
    }

    @Test
    fun 서로_다른_주문의_결제_취소_이벤트_두_개는_둘_다_보상한다() {
        val service = paymentEventService()
        service.publishPaymentCancelledEvent(successPayment(paymentId = 10L, orderId = 20L, userId = 7L))
        service.publishPaymentCancelledEvent(successPayment(paymentId = 11L, orderId = 21L, userId = 7L))

        savedOutboxEvents.toList().forEach { consumer.handleCompensationEvent(consumedMessage(it)) }

        assertThat(compensationLogs.values)
            .extracting("eventId", "orderId", "status")
            .containsExactly(
                tuple("payment-cancelled-10", 20L, CompensationEventLogStatus.COMPLETED),
                tuple("payment-cancelled-11", 21L, CompensationEventLogStatus.COMPLETED)
            )
        verify(refundPointsService).refundPoints(7L, 10L)
        verify(refundPointsService).refundPoints(7L, 11L)
    }

    @Test
    fun eventId가_빈_결제_취소_이벤트는_처리_완료로_기록하지_않고_실패한다() {
        KafkaPaymentEventPublisher(TransactionalEventPublisherImpl(OutboxEventWriter(outboxEventRepository, productionObjectMapper)))
            .publishPaymentCancelledEvent(
                PaymentCancelledEvent(
                    eventId = "",
                    paymentId = 5L,
                    orderId = 6L,
                    userId = 6L,
                    amount = BigDecimal("3000.00"),
                    transactionId = "MOCK_1794499236200_2502"
                )
            )
        val message = consumedMessage(savedOutboxEvents.single())

        assertThatThrownBy { consumer.handleCompensationEvent(message) }

        assertThat(compensationLogs).isEmpty()
        verify(compensationEventLogService, never()).markCompleted(any())
        verify(refundPointsService, never()).refundPoints(any(), any())
    }

    @Test
    fun 실제_발행_코드가_만든_결제_실패_이벤트를_읽으면_메시지의_eventId_orderId_paymentId로_기록한다() {
        val payment = Payment.create(orderId = 6L, userId = 6L, amount = BigDecimal("3000.00"), method = PaymentMethod.CREDIT_CARD)
        payment.updateStatus(newStatus = PaymentStatus.FAILED, errorMessage = "잔액 부족")
        assignId(payment, 5L)
        paymentEventService().publishPaymentFailedEvent(payment)

        consumer.handleCompensationEvent(consumedMessage(savedOutboxEvents.single()))

        assertThat(compensationLogs.values)
            .extracting("eventId", "compensationType", "paymentId", "orderId", "status")
            .containsExactly(tuple("payment-failed-5", "PAYMENT_FAILED", 5L, 6L, CompensationEventLogStatus.COMPLETED))
    }

    private fun paymentEventService() = PaymentEventService(
        KafkaPaymentEventPublisher(TransactionalEventPublisherImpl(OutboxEventWriter(outboxEventRepository, productionObjectMapper)))
    )

    private fun successPayment(paymentId: Long, orderId: Long, userId: Long): Payment {
        val payment = Payment.create(orderId = orderId, userId = userId, amount = BigDecimal("3000.00"), method = PaymentMethod.CREDIT_CARD)
        payment.updateStatus(
            newStatus = PaymentStatus.SUCCESS,
            transactionId = "MOCK_1794499236200_2502",
            completedAt = LocalDateTime.of(2026, 11, 13, 1, 0, 39)
        )
        assignId(payment, paymentId)
        return payment
    }

    private fun assignId(payment: Payment, id: Long) {
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(payment, id)
    }

    private fun consumedMessage(outboxEvent: OutboxEvent): String {
        val record = AvroEventConverter(productionObjectMapper).convertJsonToAvro(outboxEvent.eventType, outboxEvent.eventData)
        val config = mapOf(
            "schema.registry.url" to "mock://$registryScope",
            "value.subject.name.strategy" to TopicRecordNameStrategy::class.java.name
        )
        val serializer = KafkaAvroSerializer().apply { configure(config, false) }
        val deserializer = AvroJsonDeserializer().apply { configure(config, false) }
        return deserializer.deserialize(outboxEvent.topic, serializer.serialize(outboxEvent.topic, record))!!
    }

    private fun loadProductionObjectMapper(): ObjectMapper {
        lateinit var mapper: ObjectMapper
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java))
            .run { context -> mapper = context.getBean(ObjectMapper::class.java) }
        return mapper
    }
}
