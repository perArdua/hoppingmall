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

@DisplayName("환불 완료 이벤트 항목의 정가가 Avro 왕복 뒤에도 남는다")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefundCompletedProductPriceRoundTripTest {

    private val objectMapper = ObjectMapper()
    private val converter = AvroEventConverter(objectMapper)
    private val topic = "refund-completion"
    private val scope = "refund-product-price"

    @AfterEach
    fun tearDown() {
        MockSchemaRegistry.dropScope(scope)
    }

    private fun roundTrip(items: List<Map<String, Any?>>): String {
        val config = mapOf(
            "schema.registry.url" to "mock://$scope",
            "value.subject.name.strategy" to TopicRecordNameStrategy::class.java.name
        )
        val serializer = KafkaAvroSerializer().apply { configure(config, false) }
        val deserializer = AvroJsonDeserializer().apply { configure(config, false) }
        val eventData = mapOf(
            "eventType" to "RefundCompleted",
            "eventId" to "refund-evt-738",
            "refundId" to 738L,
            "orderId" to 100L,
            "paymentId" to 200L,
            "buyerId" to 300L,
            "refundAmount" to "13500",
            "items" to items
        )
        val record = converter.convertJsonToAvro("RefundCompleted", objectMapper.writeValueAsString(eventData))
        return deserializer.deserialize(topic, serializer.serialize(topic, record))!!
    }

    @Test
    fun 항목의_정가가_왕복_뒤에도_남는다() {
        val message = roundTrip(
            listOf(
                mapOf("productId" to 101L, "quantity" to 1, "refundPrice" to "13500", "orderItemId" to 9001L, "productPrice" to "15000")
            )
        )

        val item = objectMapper.readTree(message).get("items").single()
        assertThat(item.get("productPrice").asText()).isEqualTo("15000")
        assertThat(item.get("refundPrice").asText()).isEqualTo("13500")
    }

    @Test
    fun 정가가_없는_항목은_null로_남는다() {
        val message = roundTrip(listOf(mapOf("productId" to 101L, "quantity" to 1, "refundPrice" to "15000")))

        val item = objectMapper.readTree(message).get("items").single()
        assertThat(item.has("productPrice")).isTrue()
        assertThat(item.get("productPrice").isNull).isTrue()
    }
}
