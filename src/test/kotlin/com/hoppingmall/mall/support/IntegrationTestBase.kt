package com.hoppingmall.mall.support

import com.hoppingmall.mall.global.common.config.TestKafkaConfig
import com.hoppingmall.mall.global.common.config.TestRedisConfig
import com.hoppingmall.mall.global.common.service.OutboxEventService
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import jakarta.persistence.EntityManager
import java.time.Duration

@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@ActiveProfiles("test")
@Import(TestKafkaConfig::class, TestRedisConfig::class)
@EmbeddedKafka(
    partitions = 1,
    topics = ["notification", "point-earn-request", "payment", "payment-compensation", "refund-completion"],
    brokerProperties = ["listeners=PLAINTEXT://localhost:0", "port=0"]
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class IntegrationTestBase {

    @MockBean
    protected lateinit var outboxEventService: OutboxEventService

    @Autowired
    protected lateinit var entityManager: EntityManager

    @BeforeEach
    fun cleanUp() {
        entityManager.clear()
    }

    protected fun awaitUntil(
        timeout: Duration = Duration.ofSeconds(10),
        interval: Duration = Duration.ofMillis(200),
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeout.toMillis()
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(interval.toMillis())
        }
        throw AssertionError("조건이 ${timeout.seconds}초 내에 충족되지 않았습니다")
    }
}
