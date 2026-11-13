package com.hoppingmall.common.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("멤버십 되돌리기 이벤트 Avro 변환")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipRevertEventAvroTest {

    private val objectMapper = ObjectMapper()
    private val converter = AvroEventConverter(objectMapper)
    private val topic = KafkaTopics.MEMBERSHIP_UPDATE_REQUEST
    private val scope = "membership-revert"

    @AfterEach
    fun tearDown() {
        MockSchemaRegistry.dropScope(scope)
    }

    private fun config() = mapOf(
        "schema.registry.url" to "mock://$scope",
        "value.subject.name.strategy" to TopicRecordNameStrategy::class.java.name
    )

    private fun roundTrip(eventType: String, eventData: Map<String, Any?>): String {
        val serializer = KafkaAvroSerializer().apply { configure(config(), false) }
        val deserializer = AvroJsonDeserializer().apply { configure(config(), false) }
        val record = converter.convertJsonToAvro(eventType, objectMapper.writeValueAsString(eventData))
        return deserializer.deserialize(topic, serializer.serialize(topic, record))!!
    }

    @Test
    fun 환불_되돌리기_요청은_사유와_환불_id를_담은_채_왕복된다() {
        val message = roundTrip(
            "MembershipRevertRequested",
            mapOf(
                "eventId" to "membership-revert-refund-7",
                "userId" to 37L,
                "orderId" to 1L,
                "paymentId" to 2L,
                "amount" to "30000",
                "reason" to "REFUND",
                "refundId" to 7L
            )
        )

        val node = objectMapper.readTree(message)
        assertThat(node.get("eventType").asText()).isEqualTo("MembershipRevertRequested")
        assertThat(node.get("eventId").asText()).isEqualTo("membership-revert-refund-7")
        assertThat(node.get("userId").asLong()).isEqualTo(37L)
        assertThat(node.get("paymentId").asLong()).isEqualTo(2L)
        assertThat(node.get("amount").asText()).isEqualTo("30000")
        assertThat(node.get("reason").asText()).isEqualTo("REFUND")
        assertThat(node.get("refundId").asLong()).isEqualTo(7L)
    }

    @Test
    fun 취소_되돌리기_요청은_환불_id_없이_왕복된다() {
        val message = roundTrip(
            "MembershipRevertRequested",
            mapOf(
                "eventId" to "membership-revert-cancel-2",
                "userId" to 37L,
                "orderId" to 1L,
                "paymentId" to 2L,
                "amount" to "89000",
                "reason" to "CANCEL",
                "refundId" to null
            )
        )

        val node = objectMapper.readTree(message)
        assertThat(node.get("eventType").asText()).isEqualTo("MembershipRevertRequested")
        assertThat(node.get("reason").asText()).isEqualTo("CANCEL")
        assertThat(node.get("refundId").isNull).isTrue()
    }

    @Test
    fun 되돌리기와_갱신은_같은_토픽에서_레코드별_subject로_등록되고_갱신_스키마는_그대로다() {
        roundTrip(
            "MembershipUpdateRequested",
            mapOf("eventId" to "membership-TXN_1", "userId" to 37L, "orderId" to 1L, "paymentId" to 2L, "amount" to "89000")
        )
        roundTrip(
            "MembershipRevertRequested",
            mapOf(
                "eventId" to "membership-revert-cancel-2", "userId" to 37L, "orderId" to 1L, "paymentId" to 2L,
                "amount" to "89000", "reason" to "CANCEL"
            )
        )

        val subjects = MockSchemaRegistry.getClientForScope(scope).allSubjects
        assertThat(subjects).hasSize(2)
        assertThat(subjects).contains("$topic-${MembershipUpdateRequestEvent.getClassSchema().fullName}")
        assertThat(MembershipUpdateRequestEvent.getClassSchema().fields.map { it.name() })
            .containsExactly("eventId", "userId", "orderId", "paymentId", "amount", "eventType")
    }
}
