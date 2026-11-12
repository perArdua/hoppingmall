package com.hoppingmall.notification.consumer

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.notification.domain.Notification
import com.hoppingmall.notification.domain.NotificationRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.data.redis.core.RedisTemplate

@ExtendWith(MockitoExtension::class)
@DisplayName("NotificationEventConsumer 필수 필드가 잘못된 이벤트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationEventConsumerDiscardTest {

    @Mock
    private lateinit var notificationRepository: NotificationRepository

    @Mock
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Mock
    private lateinit var cacheManager: CacheManager

    private val meterRegistry = SimpleMeterRegistry()

    private val appender = ListAppender<ILoggingEvent>()

    private val consumerLogger = LoggerFactory.getLogger(NotificationEventConsumer::class.java) as Logger

    private lateinit var consumer: NotificationEventConsumer

    @BeforeEach
    fun setUp() {
        consumer = NotificationEventConsumer(
            notificationRepository,
            redisTemplate,
            ObjectMapper(),
            cacheManager,
            meterRegistry
        )
        Mockito.lenient().`when`(notificationRepository.save(any<Notification>()))
            .thenAnswer { it.arguments[0] }
        appender.start()
        consumerLogger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        consumerLogger.detachAppender(appender)
        appender.stop()
    }

    @Test
    fun 사용자_id가_0이면_저장하지_않고_경고와_지표를_남긴다() {
        consumer.handleNotificationEvent(record(event(userId = 0L)))

        verify(notificationRepository, never()).save(any<Notification>())
        assertDiscarded("invalid_user_id")
    }

    @Test
    fun 사용자_id가_음수이면_저장하지_않고_경고와_지표를_남긴다() {
        consumer.handleNotificationEvent(record(event(userId = -1L)))

        verify(notificationRepository, never()).save(any<Notification>())
        assertDiscarded("invalid_user_id")
    }

    @Test
    fun 사용자_id가_없으면_경고와_지표를_남긴다() {
        consumer.handleNotificationEvent(record(event(userId = null)))

        verify(notificationRepository, never()).save(any<Notification>())
        assertDiscarded("invalid_user_id")
    }

    @Test
    fun 사용자_id가_숫자가_아니면_경고와_지표를_남긴다() {
        consumer.handleNotificationEvent(record(event(userId = "abc")))

        verify(notificationRepository, never()).save(any<Notification>())
        assertDiscarded("invalid_user_id")
    }

    @Test
    fun snake_case_사용자_id가_0이어도_저장하지_않는다() {
        val event = event(userId = null) + ("user_id" to 0L)

        consumer.handleNotificationEvent(record(event))

        verify(notificationRepository, never()).save(any<Notification>())
        assertDiscarded("invalid_user_id")
    }

    @Test
    fun eventId가_없으면_경고와_지표를_남긴다() {
        consumer.handleNotificationEvent(record(event(userId = 1L) - "eventId"))

        verify(notificationRepository, never()).save(any<Notification>())
        assertDiscarded("missing_event_id")
    }

    @Test
    fun 제목이_없으면_경고와_지표를_남긴다() {
        consumer.handleNotificationEvent(record(event(userId = 1L) - "title"))

        verify(notificationRepository, never()).save(any<Notification>())
        assertDiscarded("missing_title")
    }

    @Test
    fun 정상_이벤트는_저장하고_버림_지표를_올리지_않는다() {
        consumer.handleNotificationEvent(record(event(userId = 7L)))

        verify(notificationRepository).save(any<Notification>())
        assertThat(meterRegistry.find(DISCARDED_METRIC).counters()).isEmpty()
        assertThat(appender.list).noneMatch { it.level == Level.WARN && it.formattedMessage.contains("reason=") }
    }

    private fun assertDiscarded(reason: String) {
        val counter = meterRegistry.find(DISCARDED_METRIC).tag("reason", reason).counter()
        assertThat(counter?.count()).isEqualTo(1.0)
        assertThat(appender.list)
            .anyMatch { it.level == Level.WARN && it.formattedMessage.contains("reason=$reason") }
    }

    private fun event(userId: Any?): Map<String, Any> {
        val base = mapOf(
            "eventId" to "evt-discard",
            "type" to "PAYMENT_CANCELLED",
            "title" to "주문이 취소되었습니다",
            "content" to "주문번호 1의 결제 취소가 완료되었습니다."
        )
        return if (userId == null) base else base + ("userId" to userId)
    }

    private fun record(event: Map<String, Any>) =
        ConsumerRecord<String, Any>("notification", 0, 0, "key", event)

    companion object {
        private const val DISCARDED_METRIC = "notification.event.discarded"
    }
}
