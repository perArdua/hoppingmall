package com.hoppingmall.notification.consumer

import com.hoppingmall.notification.domain.NotificationRepository
import com.hoppingmall.notification.dto.response.UnreadCountResponse
import com.hoppingmall.notification.service.NotificationSseService
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.atomic.AtomicReference

@SpringBootTest
@ActiveProfiles("test")
@Import(NotificationEventConsumerAfterCommitTest.TestInfraConfig::class, TestCacheConfig::class)
@TestPropertySource(properties = [
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV4,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.kafka.consumer.group-id=notification-service"
])
@DisplayName("NotificationEventConsumer 커밋 뒤 실시간 전송·캐시 삭제")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationEventConsumerAfterCommitTest {

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

    @Autowired
    private lateinit var notificationEventConsumer: NotificationEventConsumer

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun setUp() {
        notificationRepository.deleteAllInBatch()
        cacheManager.getCache(UNREAD_COUNT_CACHE)!!.clear()
        Mockito.reset(redisTemplate)
    }

    @Test
    fun 저장_트랜잭션이_롤백되면_실시간_전송과_안_읽은_수_캐시_삭제가_일어나지_않는다() {
        val cache = cacheManager.getCache(UNREAD_COUNT_CACHE)!!
        cache.put(USER_ID, UnreadCountResponse(3L))

        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            notificationEventConsumer.handleNotificationEvent(record("evt-rollback"))
            status.setRollbackOnly()
        }

        assertThat(notificationRepository.existsByEventId("evt-rollback")).isFalse()
        verify(redisTemplate, never()).convertAndSend(any<String>(), any<String>())
        assertThat(cache.get(USER_ID)?.get()).isEqualTo(UnreadCountResponse(3L))
    }

    @Test
    fun 실시간_전송은_알림이_커밋되어_다른_트랜잭션에서_보일_때_일어나고_캐시도_지워진다() {
        val cache = cacheManager.getCache(UNREAD_COUNT_CACHE)!!
        cache.put(USER_ID, UnreadCountResponse(3L))
        val visibleAtPublish = AtomicReference<Boolean>()
        val cachedAtPublish = AtomicReference<Any?>()
        whenever(redisTemplate.convertAndSend(any<String>(), any<String>())).thenAnswer {
            visibleAtPublish.set(
                newTransaction().execute { notificationRepository.existsByEventId("evt-commit") }
            )
            cachedAtPublish.set(cache.get(USER_ID)?.get())
            1L
        }

        notificationEventConsumer.handleNotificationEvent(record("evt-commit"))

        verify(redisTemplate, times(1)).convertAndSend(eq(NotificationSseService.CHANNEL_NAME), any<String>())
        assertThat(visibleAtPublish.get()).isTrue()
        assertThat(cachedAtPublish.get()).isNull()
        assertThat(cache.get(USER_ID)).isNull()
    }

    private fun newTransaction(): TransactionTemplate =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    private fun record(eventId: String): ConsumerRecord<String, Any> =
        ConsumerRecord(
            "notification", 0, 0, "key",
            mapOf(
                "eventId" to eventId,
                "userId" to USER_ID,
                "type" to "PAYMENT_COMPLETED",
                "title" to "결제 완료",
                "content" to "결제가 완료되었습니다."
            )
        )

    companion object {
        private const val UNREAD_COUNT_CACHE = "unread-count"
        private const val USER_ID = 7L
    }
}
