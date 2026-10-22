package com.hoppingmall.payment.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.common.event.AvroJsonDeserializer
import com.hoppingmall.payment.payment.domain.CompensationEventLog
import com.hoppingmall.payment.payment.domain.CompensationEventLogStatus
import com.hoppingmall.payment.payment.service.strategy.CompensationEventHandlerRegistry
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("PaymentCompensationConsumer Avro 왕복 메시지 처리")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PaymentCompensationConsumerAvroRoundTripTest {

    @Mock
    private lateinit var compensationEventLogService: CompensationEventLogService

    @Mock
    private lateinit var refundPointsService: RefundPointsService

    @Mock
    private lateinit var compensationEventHandlerRegistry: CompensationEventHandlerRegistry

    private val objectMapper = ObjectMapper()
    private lateinit var consumer: PaymentCompensationConsumer

    @BeforeEach
    fun setUp() {
        consumer = PaymentCompensationConsumer(
            compensationEventLogService,
            refundPointsService,
            compensationEventHandlerRegistry,
            objectMapper
        )
    }

    @AfterEach
    fun tearDown() {
        MockSchemaRegistry.dropScope("payment-roundtrip")
    }

    private fun roundTrip(eventType: String, eventData: Map<String, Any>): String {
        val record = AvroEventConverter(objectMapper).convertJsonToAvro(eventType, objectMapper.writeValueAsString(eventData))
        val config = mapOf(
            "schema.registry.url" to "mock://payment-roundtrip",
            "value.subject.name.strategy" to TopicRecordNameStrategy::class.java.name
        )
        val serializer = KafkaAvroSerializer().apply { configure(config, false) }
        val deserializer = AvroJsonDeserializer().apply { configure(config, false) }
        return deserializer.deserialize(KafkaTopics.PAYMENT_REVERSAL, serializer.serialize(KafkaTopics.PAYMENT_REVERSAL, record))!!
    }

    @Test
    fun 직렬화_경로를_거친_역보상_요청은_포인트_환불을_수행한다() {
        val message = roundTrip(
            "PaymentReversalRequested",
            mapOf(
                "eventType" to "PaymentReversalRequested",
                "eventId" to "reversal-tx-1",
                "orderId" to 200L,
                "paymentId" to 100L,
                "userId" to 1L,
                "reason" to "RESERVATION_EXPIRED"
            )
        )
        whenever(compensationEventLogService.saveIfAbsent(any(), any(), any(), any())).thenReturn(
            CompensationEventLog(
                eventId = "reversal-tx-1",
                compensationType = "PAYMENT_REVERSAL",
                paymentId = 100L,
                orderId = 200L,
                status = CompensationEventLogStatus.PENDING
            )
        )

        consumer.handlePaymentReversal(message)

        verify(refundPointsService).refundPoints(1L, 100L)
        verify(compensationEventLogService).markCompleted("reversal-tx-1")
    }
}
