package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.common.event.AvroJsonDeserializer
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.order.metrics.SagaCompensationMetrics
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.SagaEventLog
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.domain.repository.SagaEventLogRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import org.assertj.core.api.Assertions.assertThat
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
import org.mockito.kotlin.mock
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("OrderSagaConsumer Avro 왕복 메시지 처리")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderSagaConsumerAvroRoundTripTest {

    @Mock
    private lateinit var sagaEventLogRepository: SagaEventLogRepository

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    @Mock
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    @Mock
    private lateinit var sagaCompensationMetrics: SagaCompensationMetrics

    private val objectMapper = ObjectMapper()
    private val topic = "payment-compensation"
    private lateinit var consumer: OrderSagaConsumer

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as TransactionCallback<Any?>
            callback.doInTransaction(mock())
        }
        val savedLogs = mutableMapOf<String, SagaEventLog>()
        Mockito.lenient().`when`(sagaEventLogRepository.save(any<SagaEventLog>())).thenAnswer {
            val log = it.arguments[0] as SagaEventLog
            savedLogs[log.eventId] = log
            log
        }
        Mockito.lenient().`when`(sagaEventLogRepository.findByEventId(any())).thenAnswer { savedLogs[it.arguments[0] as String] }
        consumer = OrderSagaConsumer(
            sagaEventLogRepository,
            orderRepository,
            orderItemRepository,
            inventoryCommandPort,
            transactionalEventPublisher,
            objectMapper,
            KafkaEventMapper(objectMapper),
            transactionTemplate,
            sagaCompensationMetrics
        )
    }

    @AfterEach
    fun tearDown() {
        MockSchemaRegistry.dropScope("order-roundtrip")
    }

    private fun cancelRequestedOrder(): Order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000")).apply {
        updateStatus(OrderStatus.PAYING)
        updateStatus(OrderStatus.PAID)
        updateStatus(OrderStatus.CANCEL_REQUESTED)
    }

    private fun outboxEventData(): String = objectMapper.writeValueAsString(
        mapOf(
            "eventType" to "PaymentCancellationRequested",
            "eventId" to "cancel-1-1700000000000",
            "orderId" to 1L
        )
    )

    private fun roundTrip(eventData: String): String {
        val record = AvroEventConverter(objectMapper).convertJsonToAvro("PaymentCancellationRequested", eventData)
        val config = mapOf(
            "schema.registry.url" to "mock://order-roundtrip",
            "value.subject.name.strategy" to TopicRecordNameStrategy::class.java.name
        )
        val serializer = KafkaAvroSerializer().apply { configure(config, false) }
        val deserializer = AvroJsonDeserializer().apply { configure(config, false) }
        return deserializer.deserialize(topic, serializer.serialize(topic, record))!!
    }

    @Test
    fun 직렬화_경로를_거친_취소_요청_메시지는_CANCEL_REQUESTED_주문을_CANCELLED로_바꾸지_않는다() {
        val order = cancelRequestedOrder()
        Mockito.lenient().`when`(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        val message = roundTrip(outboxEventData())

        consumer.handlePaymentCompensation(message)

        assertThat(order.status).isEqualTo(OrderStatus.CANCEL_REQUESTED)
    }
}
