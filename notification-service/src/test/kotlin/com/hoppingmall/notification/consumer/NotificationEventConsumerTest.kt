package com.hoppingmall.notification.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.notification.domain.Notification
import com.hoppingmall.notification.domain.NotificationRepository
import com.hoppingmall.notification.enums.NotificationType
import com.hoppingmall.notification.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCache
import org.springframework.data.redis.core.RedisTemplate

@ExtendWith(MockitoExtension::class)
@DisplayName("NotificationEventConsumer")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationEventConsumerTest {

    @Mock
    private lateinit var notificationRepository: NotificationRepository

    @Mock
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @Mock
    private lateinit var cacheManager: CacheManager

    @InjectMocks
    private lateinit var notificationEventConsumer: NotificationEventConsumer

    private fun stubPublishSseEvent() {
        whenever(cacheManager.getCache("unread-count")).thenReturn(ConcurrentMapCache("unread-count"))
        whenever(objectMapper.writeValueAsString(any())).thenReturn("{}")
        whenever(redisTemplate.convertAndSend(any<String>(), any<String>())).thenReturn(1L)
    }

    private fun createSavedNotification(eventId: String): Notification {
        return Notification(
            eventId = eventId, userId = 1L, type = NotificationType.PAYMENT_COMPLETED,
            title = "결제", content = "내용"
        ).withId(1L)
    }

    @Test
    fun 알림_이벤트를_처리한다() {
        val event = mapOf(
            "eventId" to "evt-001",
            "userId" to 1L,
            "type" to "PAYMENT_COMPLETED",
            "title" to "결제 완료",
            "content" to "결제가 완료되었습니다.",
            "metadata" to "{}"
        )
        val record = ConsumerRecord<String, Any>("notification", 0, 0, "key", event)

        whenever(notificationRepository.existsByEventId("evt-001")).thenReturn(false)
        whenever(notificationRepository.save(any<Notification>())).thenReturn(createSavedNotification("evt-001"))
        stubPublishSseEvent()

        notificationEventConsumer.handleNotificationEvent(record)

        val captor = argumentCaptor<Notification>()
        verify(notificationRepository).save(captor.capture())
        assertThat(captor.firstValue.eventId).isEqualTo("evt-001")
        assertThat(captor.firstValue.type).isEqualTo(NotificationType.PAYMENT_COMPLETED)
    }

    @Test
    fun 중복_이벤트는_무시한다() {
        val event = mapOf(
            "eventId" to "evt-001",
            "userId" to 1L,
            "type" to "PAYMENT_COMPLETED",
            "title" to "결제 완료",
            "content" to "결제가 완료되었습니다."
        )
        val record = ConsumerRecord<String, Any>("notification", 0, 0, "key", event)
        whenever(notificationRepository.existsByEventId("evt-001")).thenReturn(true)

        notificationEventConsumer.handleNotificationEvent(record)

        verify(notificationRepository, never()).save(any())
    }

    @Test
    fun eventId가_없으면_무시한다() {
        val event = mapOf(
            "userId" to 1L,
            "type" to "PAYMENT_COMPLETED",
            "title" to "결제 완료",
            "content" to "결제가 완료되었습니다."
        )
        val record = ConsumerRecord<String, Any>("notification", 0, 0, "key", event)

        notificationEventConsumer.handleNotificationEvent(record)

        verify(notificationRepository, never()).existsByEventId(any())
    }

    @Test
    fun userId가_없으면_무시한다() {
        val event = mapOf(
            "eventId" to "evt-001",
            "type" to "PAYMENT_COMPLETED",
            "title" to "결제 완료",
            "content" to "결제가 완료되었습니다."
        )
        val record = ConsumerRecord<String, Any>("notification", 0, 0, "key", event)

        notificationEventConsumer.handleNotificationEvent(record)

        verify(notificationRepository, never()).existsByEventId(any())
    }

    @Test
    fun type이_없으면_무시한다() {
        val event = mapOf(
            "eventId" to "evt-001",
            "userId" to 1L,
            "title" to "결제 완료",
            "content" to "결제가 완료되었습니다."
        )
        val record = ConsumerRecord<String, Any>("notification", 0, 0, "key", event)

        notificationEventConsumer.handleNotificationEvent(record)

        verify(notificationRepository, never()).existsByEventId(any())
    }

    @Test
    fun 알_수_없는_알림_타입이면_저장하지_않는다() {
        val event = mapOf(
            "eventId" to "evt-001",
            "userId" to 1L,
            "type" to "UNKNOWN_TYPE",
            "title" to "결제 완료",
            "content" to "결제가 완료되었습니다."
        )
        val record = ConsumerRecord<String, Any>("notification", 0, 0, "key", event)
        whenever(notificationRepository.existsByEventId("evt-001")).thenReturn(false)

        notificationEventConsumer.handleNotificationEvent(record)

        verify(notificationRepository, never()).save(any())
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun String_타입_이벤트를_처리한다() {
        val jsonString = """{"eventId":"evt-002","userId":1,"type":"PAYMENT_COMPLETED","title":"결제","content":"내용"}"""
        val parsedMap = mapOf(
            "eventId" to "evt-002",
            "userId" to 1,
            "type" to "PAYMENT_COMPLETED",
            "title" to "결제",
            "content" to "내용"
        )
        val record = ConsumerRecord<String, Any>("notification", 0, 0, "key", jsonString)

        whenever(objectMapper.readValue(eq(jsonString), eq(Map::class.java))).thenReturn(parsedMap as Map<Any, Any>)
        whenever(notificationRepository.existsByEventId("evt-002")).thenReturn(false)
        whenever(notificationRepository.save(any<Notification>())).thenReturn(createSavedNotification("evt-002"))
        stubPublishSseEvent()

        notificationEventConsumer.handleNotificationEvent(record)

        verify(notificationRepository).save(any())
    }

    @Test
    fun SSE_발행_실패시에도_정상_처리된다() {
        val event = mapOf(
            "eventId" to "evt-003",
            "userId" to 1L,
            "type" to "PAYMENT_COMPLETED",
            "title" to "결제",
            "content" to "내용"
        )
        val record = ConsumerRecord<String, Any>("notification", 0, 0, "key", event)

        whenever(notificationRepository.existsByEventId("evt-003")).thenReturn(false)
        whenever(notificationRepository.save(any<Notification>())).thenReturn(createSavedNotification("evt-003"))
        whenever(cacheManager.getCache("unread-count")).thenReturn(ConcurrentMapCache("unread-count"))
        whenever(objectMapper.writeValueAsString(any())).thenThrow(RuntimeException("직렬화 실패"))

        notificationEventConsumer.handleNotificationEvent(record)

        verify(notificationRepository).save(any())
    }

    @Test
    fun snake_case_키_이벤트를_처리한다() {
        val event = mapOf(
            "event_id" to "evt-004",
            "user_id" to 1L,
            "type" to "PAYMENT_COMPLETED",
            "title" to "결제",
            "content" to "내용"
        )
        val record = ConsumerRecord<String, Any>("notification", 0, 0, "key", event)

        whenever(notificationRepository.existsByEventId("evt-004")).thenReturn(false)
        whenever(notificationRepository.save(any<Notification>())).thenReturn(createSavedNotification("evt-004"))
        stubPublishSseEvent()

        notificationEventConsumer.handleNotificationEvent(record)

        verify(notificationRepository).save(any())
    }
}
