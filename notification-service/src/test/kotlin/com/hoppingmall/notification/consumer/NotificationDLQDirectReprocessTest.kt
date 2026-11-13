package com.hoppingmall.notification.consumer

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import com.hoppingmall.dlq.publisher.DLQMessagePublisher
import com.hoppingmall.dlq.service.DLQCommandService
import com.hoppingmall.notification.config.KafkaConsumerConfig
import com.hoppingmall.notification.domain.NotificationRepository
import com.hoppingmall.notification.support.TestCacheConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.kafka.support.KafkaUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
@ActiveProfiles("test")
@Import(NotificationDLQDirectReprocessTest.TestInfraConfig::class, TestCacheConfig::class)
@TestPropertySource(properties = [
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV4,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.kafka.consumer.group-id=notification-service"
])
@DisplayName("알림 컨슈머 DLQ 메시지의 소비 그룹별 직접 재처리")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationDLQDirectReprocessTest {

    @TestConfiguration
    class TestInfraConfig {

        @Bean
        @Primary
        fun redisConnectionFactory(): RedisConnectionFactory =
            Mockito.mock(RedisConnectionFactory::class.java)

        @Bean
        @Primary
        @Suppress("UNCHECKED_CAST")
        fun redisTemplate(): RedisTemplate<String, String> =
            Mockito.mock(RedisTemplate::class.java) as RedisTemplate<String, String>

        @Bean
        @Primary
        fun redisMessageListenerContainer(): RedisMessageListenerContainer =
            Mockito.mock(RedisMessageListenerContainer::class.java)
    }

    @MockitoBean
    private lateinit var dlqMessagePublisher: DLQMessagePublisher

    @Autowired
    private lateinit var dlqCommandService: DLQCommandService

    @Autowired
    private lateinit var dlqMessageRepository: DLQMessageRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var notificationEventConsumer: NotificationEventConsumer

    @BeforeEach
    fun setUp() {
        dlqMessageRepository.deleteAllInBatch()
        notificationRepository.deleteAllInBatch()
    }

    @Test
    fun 알림_컨슈머가_재시도를_다_쓴_메시지는_소비_그룹과_함께_DLQ에_저장된다() {
        recoverInListenerThread(record("evt-dlq-group"))

        val saved = dlqMessageRepository.findAll().single()
        assertThat(saved.originalTopic).isEqualTo(KafkaTopics.NOTIFICATION)
        assertThat(saved.consumerGroup).isEqualTo(NOTIFICATION_GROUP)
        assertThat(saved.status).isEqualTo(DLQStatus.PENDING)
    }

    @Test
    fun DLQ_재처리는_원래_토픽에_다시_발행하지_않고_알림을_직접_저장한다() {
        recoverInListenerThread(record("evt-dlq-direct"))
        val dlqMessageId = dlqMessageRepository.findAll().single().id!!

        val reprocessed = dlqCommandService.retryDLQMessage(dlqMessageId)

        assertThat(reprocessed).isTrue()
        verify(dlqMessagePublisher, never()).publish(any(), any(), anyOrNull())
        assertThat(notificationRepository.existsByEventId("evt-dlq-direct")).isTrue()
        val after = dlqMessageRepository.findById(dlqMessageId).orElseThrow()
        assertThat(after.status).isEqualTo(DLQStatus.PROCESSED)
        assertThat(after.retryCount).isEqualTo(1)
    }

    @Test
    fun 이미_저장된_알림의_DLQ_재처리는_알림을_하나_더_만들지_않고_처리_완료로_끝난다() {
        val record = record("evt-dlq-duplicate")
        notificationEventConsumer.handleNotificationEvent(record)
        recoverInListenerThread(record)
        val dlqMessageId = dlqMessageRepository.findAll().single().id!!

        val reprocessed = dlqCommandService.retryDLQMessage(dlqMessageId)

        assertThat(reprocessed).isTrue()
        verify(dlqMessagePublisher, never()).publish(any(), any(), anyOrNull())
        assertThat(notificationRepository.findAll().count { it.eventId == "evt-dlq-duplicate" }).isEqualTo(1)
        assertThat(dlqMessageRepository.findById(dlqMessageId).orElseThrow().status).isEqualTo(DLQStatus.PROCESSED)
    }

    private fun recoverInListenerThread(record: ConsumerRecord<String, Any>) {
        val recoverer = KafkaConsumerConfig(
            "localhost:9092", NOTIFICATION_GROUP, "http://localhost:8081", dlqCommandService
        ).deadLetterRecoverer()
        KafkaUtils.setConsumerGroupId(NOTIFICATION_GROUP)
        try {
            recoverer.accept(record, RuntimeException("알림 저장 일시 장애"))
        } finally {
            KafkaUtils.clearConsumerGroupId()
        }
    }

    private fun record(eventId: String): ConsumerRecord<String, Any> =
        ConsumerRecord(
            KafkaTopics.NOTIFICATION, 0, 42L, USER_ID.toString(),
            """{"event_id":"$eventId","user_id":$USER_ID,"type":"PAYMENT_COMPLETED","title":"결제 완료","content":"결제가 완료되었습니다."}"""
        )

    companion object {
        private const val NOTIFICATION_GROUP = "notification-service"
        private const val USER_ID = 7L
    }
}
