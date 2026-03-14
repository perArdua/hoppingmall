package com.hoppingmall.mall.global.common.service

import com.hoppingmall.mall.global.common.config.DeadLetterMessage
import com.hoppingmall.mall.global.common.config.TestKafkaConfig
import com.hoppingmall.mall.global.common.config.TestRedisConfig
import com.hoppingmall.mall.global.common.domain.DLQStatus
import com.hoppingmall.mall.global.common.domain.repository.DLQMessageRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@ActiveProfiles("test")
@Import(TestKafkaConfig::class, TestRedisConfig::class)
@EmbeddedKafka(
    partitions = 1,
    topics = ["notification", "point-earn-request", "payment", "payment-compensation"],
    brokerProperties = ["listeners=PLAINTEXT://localhost:0", "port=0"]
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("DLQService 통합 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQServiceIntegrationTest {

    @MockBean
    private lateinit var outboxEventService: OutboxEventService

    @MockBean
    private lateinit var redisMessageListenerContainer: RedisMessageListenerContainer

    @Autowired
    private lateinit var dlqService: DLQService

    @Autowired
    private lateinit var dlqMessageRepository: DLQMessageRepository

    @Test
    fun DLQ_메시지를_저장한다() {
        val deadLetterMessage = DeadLetterMessage(
            originalTopic = "payment",
            originalPartition = 0,
            originalOffset = 1L,
            originalKey = "key-1",
            originalValue = """{"orderId": 1}""",
            exception = "테스트 오류",
            timestamp = System.currentTimeMillis()
        )

        dlqService.saveDLQMessage(deadLetterMessage)

        val saved = dlqMessageRepository.findAll()
            .find { it.originalTopic == "payment" && it.originalOffset == 1L }
        assertThat(saved).isNotNull
        assertThat(saved!!.originalKey).isEqualTo("key-1")
        assertThat(saved.status).isEqualTo(DLQStatus.PENDING)
    }

    @Test
    fun 중복_DLQ_메시지는_무시된다() {
        val deadLetterMessage = DeadLetterMessage(
            originalTopic = "notification",
            originalPartition = 0,
            originalOffset = 100L,
            originalKey = "key-dup",
            originalValue = """{"test": true}""",
            exception = "중복 테스트",
            timestamp = System.currentTimeMillis()
        )

        dlqService.saveDLQMessage(deadLetterMessage)
        dlqService.saveDLQMessage(deadLetterMessage)

        val count = dlqMessageRepository.findAll()
            .count { it.originalTopic == "notification" && it.originalOffset == 100L }
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun DLQ_통계를_조회한다() {
        dlqService.saveDLQMessage(
            DeadLetterMessage(
                originalTopic = "payment",
                originalPartition = 0,
                originalOffset = 200L,
                originalKey = null,
                originalValue = "msg1",
                exception = "error1",
                timestamp = System.currentTimeMillis()
            )
        )
        dlqService.saveDLQMessage(
            DeadLetterMessage(
                originalTopic = "payment",
                originalPartition = 0,
                originalOffset = 201L,
                originalKey = null,
                originalValue = "msg2",
                exception = "error2",
                timestamp = System.currentTimeMillis()
            )
        )

        val stats = dlqService.getDLQStats()

        assertThat(stats["totalMessages"] as Long).isGreaterThanOrEqualTo(2L)
        assertThat(stats["pendingCount"] as Long).isGreaterThanOrEqualTo(2L)
    }

    @Test
    fun DLQ_메시지를_재처리한다() {
        dlqService.saveDLQMessage(
            DeadLetterMessage(
                originalTopic = "notification",
                originalPartition = 0,
                originalOffset = 300L,
                originalKey = "retry-key",
                originalValue = """{"eventId":"retry-test"}""",
                exception = "재시도 테스트",
                timestamp = System.currentTimeMillis()
            )
        )

        val saved = dlqMessageRepository.findAll()
            .find { it.originalOffset == 300L && it.originalTopic == "notification" }
        assertThat(saved).isNotNull

        val result = dlqService.retryDLQMessage(saved!!.id!!)
        assertThat(result).isTrue()

        val updated = dlqMessageRepository.findById(saved.id!!).get()
        assertThat(updated.status).isEqualTo(DLQStatus.PROCESSED)
        assertThat(updated.retryCount).isEqualTo(1)
    }
}
