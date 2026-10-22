package com.hoppingmall.user.consumer

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.common.event.AvroJsonDeserializer
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.user.domain.MembershipEventLog
import com.hoppingmall.user.domain.repository.MembershipEventLogRepository
import com.hoppingmall.user.service.MembershipCommandService
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
@DisplayName("MembershipEventConsumer Avro 왕복 메시지 처리")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipEventConsumerAvroRoundTripTest {

    @Mock
    private lateinit var membershipCommandService: MembershipCommandService

    @Mock
    private lateinit var membershipEventLogRepository: MembershipEventLogRepository

    private val globalObjectMapper = jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    @AfterEach
    fun tearDown() {
        MockSchemaRegistry.dropScope("membership-roundtrip")
    }

    private fun roundTrip(): String {
        val eventData = jacksonObjectMapper().writeValueAsString(
            mapOf(
                "eventId" to "membership-TXN_1",
                "userId" to 37L,
                "orderId" to 1L,
                "paymentId" to 1L,
                "amount" to "89000.0"
            )
        )
        val record = AvroEventConverter(globalObjectMapper).convertJsonToAvro("MembershipUpdateRequested", eventData)
        val config = mapOf(
            "schema.registry.url" to "mock://membership-roundtrip",
            "value.subject.name.strategy" to TopicRecordNameStrategy::class.java.name
        )
        val serializer = KafkaAvroSerializer().apply { configure(config, false) }
        val deserializer = AvroJsonDeserializer().apply { configure(config, false) }
        val topic = KafkaTopics.MEMBERSHIP_UPDATE_REQUEST
        return deserializer.deserialize(topic, serializer.serialize(topic, record))!!
    }

    @Test
    fun 직렬화_경로를_거친_멤버십_요청은_구매금액을_누적하고_이력을_남긴다() {
        val consumer = MembershipEventConsumer(
            membershipCommandService,
            membershipEventLogRepository,
            KafkaEventMapper(globalObjectMapper)
        )
        whenever(membershipEventLogRepository.existsByEventId("membership-TXN_1")).thenReturn(false)

        consumer.handleMembershipUpdateRequest(roundTrip())

        verify(membershipCommandService).addPurchaseAmount(37L, BigDecimal("89000.0"))
        val captor = argumentCaptor<MembershipEventLog>()
        verify(membershipEventLogRepository).save(captor.capture())
        assertThat(captor.firstValue)
            .extracting("eventId", "orderId", "paymentId")
            .containsExactly("membership-TXN_1", 1L, 1L)
    }
}
