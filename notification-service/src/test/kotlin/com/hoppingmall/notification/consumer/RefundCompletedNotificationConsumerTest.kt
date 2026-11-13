package com.hoppingmall.notification.consumer

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.common.event.AvroJsonDeserializer
import com.hoppingmall.notification.domain.Notification
import com.hoppingmall.notification.domain.NotificationRepository
import com.hoppingmall.notification.service.NotificationSseService
import com.hoppingmall.notification.support.withId
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple.tuple
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCache
import org.springframework.data.redis.core.RedisTemplate

@ExtendWith(MockitoExtension::class)
@DisplayName("NotificationEventConsumer 환불 완료 알림 이벤트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefundCompletedNotificationConsumerTest {

    @Mock
    private lateinit var notificationRepository: NotificationRepository

    @Mock
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Mock
    private lateinit var cacheManager: CacheManager

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    private val savedNotifications = mutableListOf<Notification>()

    private val unreadCountCache = ConcurrentMapCache("unread-count")

    private lateinit var consumer: NotificationEventConsumer

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(notificationRepository.existsByEventId(any())).thenAnswer { invocation ->
            savedNotifications.any { it.eventId == invocation.arguments[0] }
        }
        Mockito.lenient().`when`(notificationRepository.save(any<Notification>())).thenAnswer { invocation ->
            (invocation.arguments[0] as Notification).withId(savedNotifications.size + 1L)
                .also { savedNotifications.add(it) }
        }
        Mockito.lenient().`when`(cacheManager.getCache("unread-count")).thenReturn(unreadCountCache)
        consumer = NotificationEventConsumer(
            notificationRepository,
            redisTemplate,
            objectMapper,
            cacheManager,
            null
        )
    }

    @AfterEach
    fun tearDown() {
        MockSchemaRegistry.dropScope(SCHEMA_SCOPE)
    }

    private fun roundTrip(eventType: String, eventData: Map<String, Any>): ConsumerRecord<String, Any> {
        val record = AvroEventConverter(objectMapper)
            .convertJsonToAvro(eventType, objectMapper.writeValueAsString(eventData))
        val config = mapOf(
            "schema.registry.url" to "mock://$SCHEMA_SCOPE",
            "value.subject.name.strategy" to TopicRecordNameStrategy::class.java.name
        )
        val serializer = KafkaAvroSerializer().apply { configure(config, false) }
        val deserializer = AvroJsonDeserializer().apply { configure(config, false) }
        val value = deserializer.deserialize(KafkaTopics.NOTIFICATION, serializer.serialize(KafkaTopics.NOTIFICATION, record))!!
        return ConsumerRecord(KafkaTopics.NOTIFICATION, 0, 0, eventData["userId"].toString(), value)
    }

    private fun refundCompletedEventData(refundId: Long) = mapOf(
        "eventId" to "refund-completed-notification-$refundId",
        "userId" to 37L,
        "type" to "REFUND_COMPLETED",
        "title" to "환불이 완료되었습니다",
        "content" to "주문번호 900006의 환불이 완료되었습니다. 환불 금액: 2500원",
        "metadata" to """{"orderId":900006,"refundId":$refundId,"amount":"2500"}"""
    )

    @Test
    fun 직렬화_경로를_거친_환불_완료_알림은_환불_완료_알림으로_저장되고_전송된다() {
        consumer.handleNotificationEvent(roundTrip(REFUND_EVENT_TYPE, refundCompletedEventData(41L)))

        assertThat(savedNotifications)
            .extracting({ it.eventId }, { it.userId }, { it.type.name }, { it.title }, { it.metadata })
            .containsExactly(
                tuple(
                    "refund-completed-notification-41",
                    37L,
                    "REFUND_COMPLETED",
                    "환불이 완료되었습니다",
                    """{"orderId":900006,"refundId":41,"amount":"2500"}"""
                )
            )
        verify(redisTemplate, times(1)).convertAndSend(eq(NotificationSseService.CHANNEL_NAME), any<String>())
    }

    @Test
    fun 같은_환불_완료_알림이_다시_전달되면_한_번만_저장된다() {
        consumer.handleNotificationEvent(roundTrip(REFUND_EVENT_TYPE, refundCompletedEventData(41L)))
        consumer.handleNotificationEvent(roundTrip(REFUND_EVENT_TYPE, refundCompletedEventData(41L)))

        assertThat(savedNotifications).extracting<String> { it.eventId }
            .containsExactly("refund-completed-notification-41")
    }

    @Test
    fun 같은_주문의_부분_환불_두_건은_알림_두_건으로_저장된다() {
        consumer.handleNotificationEvent(roundTrip(REFUND_EVENT_TYPE, refundCompletedEventData(41L)))
        consumer.handleNotificationEvent(roundTrip(REFUND_EVENT_TYPE, refundCompletedEventData(42L)))

        assertThat(savedNotifications).extracting<String> { it.eventId }
            .containsExactly("refund-completed-notification-41", "refund-completed-notification-42")
    }

    @Test
    fun 결제_취소_알림도_같은_경로로_저장된다() {
        val eventData = mapOf(
            "eventId" to "payment-cancelled-notification-11",
            "userId" to 37L,
            "type" to "PAYMENT_CANCELLED",
            "title" to "결제가 취소되었습니다",
            "content" to "주문번호 900006의 결제가 취소되었습니다. 취소 금액: 2500원",
            "metadata" to """{"orderId":900006,"paymentId":11,"amount":"2500"}"""
        )

        consumer.handleNotificationEvent(roundTrip("PaymentCancelledNotificationRequested", eventData))

        assertThat(savedNotifications).extracting<String> { it.type.name }.containsExactly("PAYMENT_CANCELLED")
    }

    companion object {
        private const val SCHEMA_SCOPE = "notification-refund-roundtrip"
        private const val REFUND_EVENT_TYPE = "RefundCompletedNotificationRequested"
    }
}
