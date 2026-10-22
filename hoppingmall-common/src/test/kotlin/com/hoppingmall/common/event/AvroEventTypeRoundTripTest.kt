package com.hoppingmall.common.event

import com.fasterxml.jackson.databind.ObjectMapper
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("Avro 직렬화 왕복 시 eventType 보존")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AvroEventTypeRoundTripTest {

    private val objectMapper = ObjectMapper()
    private val converter = AvroEventConverter(objectMapper)
    private val topic = "payment-compensation"
    private val scope = "roundtrip"

    @AfterEach
    fun tearDown() {
        MockSchemaRegistry.dropScope(scope)
    }

    private fun config() = mapOf(
        "schema.registry.url" to "mock://$scope",
        "value.subject.name.strategy" to TopicRecordNameStrategy::class.java.name
    )

    private fun roundTrip(eventType: String, eventData: Map<String, Any>): String {
        val serializer = KafkaAvroSerializer().apply { configure(config(), false) }
        val deserializer = AvroJsonDeserializer().apply { configure(config(), false) }
        val record = converter.convertJsonToAvro(eventType, objectMapper.writeValueAsString(eventData))
        return deserializer.deserialize(topic, serializer.serialize(topic, record))!!
    }

    @Test
    fun PaymentCancellationRequested_왕복_후_eventType이_보존된다() {
        val message = roundTrip(
            "PaymentCancellationRequested",
            mapOf("eventType" to "PaymentCancellationRequested", "eventId" to "cancel-1-1700000000000", "orderId" to 1L)
        )

        assertThat(objectMapper.readTree(message).get("eventType").asText()).isEqualTo("PaymentCancellationRequested")
    }

    @Test
    fun eventData에_eventType이_없어도_outbox_eventType이_주입된다() {
        val message = roundTrip(
            "PaymentFailed",
            mapOf(
                "eventId" to "evt-failed-1",
                "paymentId" to 10L,
                "orderId" to 1L,
                "userId" to 100L,
                "amount" to "50000",
                "reason" to "LIMIT"
            )
        )

        assertThat(objectMapper.readTree(message).get("eventType").asText()).isEqualTo("PaymentFailed")
    }

    @Test
    fun 같은_토픽의_서로_다른_레코드는_레코드별_subject로_등록된다() {
        roundTrip(
            "PaymentFailed",
            mapOf("eventId" to "evt-failed-1", "paymentId" to 10L, "orderId" to 1L, "userId" to 100L, "amount" to "50000")
        )
        roundTrip(
            "PaymentCancellationRequested",
            mapOf("eventId" to "cancel-1", "orderId" to 1L)
        )

        assertThat(MockSchemaRegistry.getClientForScope(scope).allSubjects).containsExactlyInAnyOrder(
            "$topic-${PaymentFailedEvent.getClassSchema().fullName}",
            "$topic-${PaymentCancellationRequestedEvent.getClassSchema().fullName}"
        )
    }
}
