package com.hoppingmall.payment.point.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.common.event.AvroJsonDeserializer
import com.hoppingmall.common.event.KafkaEventMapper
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.payment.point.domain.Point
import com.hoppingmall.payment.point.domain.PointHistory
import com.hoppingmall.payment.point.domain.PointHistoryRepository
import com.hoppingmall.payment.point.domain.PointRepository
import com.hoppingmall.payment.point.enum.PointType
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.cache.CacheManager
import org.springframework.test.util.ReflectionTestUtils

@DisplayName("PointEventConsumer Avro 왕복 메시지 처리")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class PointEventConsumerAvroRoundTripTest {

    @Mock
    private lateinit var pointRepository: PointRepository

    @Mock
    private lateinit var pointHistoryRepository: PointHistoryRepository

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Mock
    private lateinit var cacheManager: CacheManager

    @Mock
    private lateinit var pointDomainService: PointDomainService

    private val globalObjectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    private val savedHistories = mutableListOf<PointHistory>()
    private lateinit var consumer: PointEventConsumer

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(pointHistoryRepository.existsByEventId(any())).thenAnswer { invocation ->
            savedHistories.any { it.eventId == invocation.arguments[0] }
        }
        Mockito.lenient().`when`(pointHistoryRepository.save(any<PointHistory>())).thenAnswer { invocation ->
            (invocation.arguments[0] as PointHistory).also { savedHistories.add(it) }
        }
        Mockito.lenient().`when`(pointDomainService.findOrCreatePoint(any())).thenAnswer { invocation ->
            Point(userId = invocation.arguments[0] as Long).also { ReflectionTestUtils.setField(it, "id", 1L) }
        }
        Mockito.lenient().`when`(pointRepository.save(any<Point>())).thenAnswer { it.arguments[0] }
        consumer = PointEventConsumer(
            pointRepository,
            pointHistoryRepository,
            transactionalEventPublisher,
            globalObjectMapper,
            KafkaEventMapper(globalObjectMapper),
            cacheManager,
            pointDomainService
        )
    }

    @AfterEach
    fun tearDown() {
        MockSchemaRegistry.dropScope("point-roundtrip")
    }

    private fun roundTrip(eventId: String, userId: Long, orderId: Long, paymentId: Long, earnAmount: String): String {
        val eventData = globalObjectMapper.copy().setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            .writeValueAsString(
                mapOf(
                    "eventId" to eventId,
                    "userId" to userId,
                    "orderId" to orderId,
                    "paymentId" to paymentId,
                    "earnAmount" to earnAmount,
                    "reason" to "결제 완료 적립"
                )
            )
        val record = AvroEventConverter(globalObjectMapper).convertJsonToAvro("PointEarnRequested", eventData)
        val config = mapOf(
            "schema.registry.url" to "mock://point-roundtrip",
            "value.subject.name.strategy" to TopicRecordNameStrategy::class.java.name
        )
        val serializer = KafkaAvroSerializer().apply { configure(config, false) }
        val deserializer = AvroJsonDeserializer().apply { configure(config, false) }
        return deserializer.deserialize(KafkaTopics.POINT_EARN_REQUEST, serializer.serialize(KafkaTopics.POINT_EARN_REQUEST, record))!!
    }

    @Test
    fun 직렬화_경로를_거친_적립_요청은_식별자가_채워진_적립_이력을_남긴다() {
        consumer.handlePointEarnRequest(roundTrip("point-earn-tx-1", 37L, 900006L, 900011L, "890"))

        assertThat(savedHistories)
            .extracting("userId", "orderId", "paymentId", "eventId", "type")
            .containsExactly(tuple(37L, 900006L, 900011L, "point-earn-tx-1", PointType.EARN))
    }

    @Test
    fun 서로_다른_결제_두_건은_적립_이력_두_건을_남긴다() {
        consumer.handlePointEarnRequest(roundTrip("point-earn-tx-1", 37L, 1L, 11L, "890"))
        consumer.handlePointEarnRequest(roundTrip("point-earn-tx-2", 37L, 2L, 12L, "450"))

        assertThat(savedHistories.filter { it.type == PointType.EARN })
            .extracting("paymentId")
            .containsExactly(11L, 12L)
    }
}
