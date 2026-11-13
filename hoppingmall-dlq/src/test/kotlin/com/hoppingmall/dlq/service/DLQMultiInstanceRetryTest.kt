package com.hoppingmall.dlq.service

import com.hoppingmall.dlq.archival.NoOpDLQArchivalService
import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.DeadLetterMessage
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import com.hoppingmall.dlq.metrics.NoOpDLQMetrics
import com.hoppingmall.dlq.publisher.DLQMessagePublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DisplayName("DLQ 재처리 여러 인스턴스 동시 실행")
@DisplayNameGeneration(ReplaceUnderscores::class)
@DataJpaTest(properties = ["spring.datasource.url=jdbc:h2:mem:dlq-multi-instance;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [DLQMultiInstanceRetryTest.TestConfig::class])
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DLQMultiInstanceRetryTest {

    @Autowired
    @Qualifier("instanceA")
    private lateinit var instanceA: DLQCommandService

    @Autowired
    @Qualifier("instanceB")
    private lateinit var instanceB: DLQCommandService

    @Autowired
    private lateinit var dlqMessageRepository: DLQMessageRepository

    @Autowired
    private lateinit var publisher: BlockingPublisher

    private val executor = Executors.newFixedThreadPool(2)

    @BeforeEach
    fun setUp() {
        dlqMessageRepository.deleteAll()
        publisher.reset()
    }

    @AfterEach
    fun tearDown() {
        publisher.releaseFirst.countDown()
        executor.shutdownNow()
    }

    @Test
    fun 두_인스턴스가_같은_메시지를_동시에_재처리해도_한_번만_재발행한다() {
        val id = saveFailure()

        val first = executor.submit<Boolean> { instanceA.retryDLQMessage(id) }
        assertThat(publisher.firstEntered.await(5, TimeUnit.SECONDS)).isTrue()

        val second = executor.submit<Result<Boolean>> { runCatching { instanceB.retryDLQMessage(id) } }
        publisher.secondPublished.await(2, TimeUnit.SECONDS)
        publisher.releaseFirst.countDown()

        assertThat(first.get(10, TimeUnit.SECONDS)).isTrue()
        assertThat(second.get(10, TimeUnit.SECONDS).getOrNull()).isFalse()
        assertThat(publisher.published).hasSize(1)
        val saved = dlqMessageRepository.findByIdOrNull(id)!!
        assertThat(saved.status).isEqualTo(DLQStatus.PROCESSED)
        assertThat(saved.retryCount).isEqualTo(1)
    }

    @Test
    fun 앞_인스턴스의_재발행이_실패해_다음_재시도로_미뤄지면_뒤_인스턴스는_바로_다시_재발행하지_않는다() {
        val id = saveFailure()
        publisher.firstResult = false

        val first = executor.submit<Boolean> { instanceA.retryDLQMessage(id) }
        assertThat(publisher.firstEntered.await(5, TimeUnit.SECONDS)).isTrue()

        val second = executor.submit<Result<Boolean>> { runCatching { instanceB.retryDLQMessage(id) } }
        publisher.secondPublished.await(2, TimeUnit.SECONDS)
        publisher.releaseFirst.countDown()

        assertThat(first.get(10, TimeUnit.SECONDS)).isFalse()
        assertThat(second.get(10, TimeUnit.SECONDS).getOrNull()).isFalse()
        assertThat(publisher.published).hasSize(1)
        val saved = dlqMessageRepository.findByIdOrNull(id)!!
        assertThat(saved.status).isEqualTo(DLQStatus.PENDING)
        assertThat(saved.retryCount).isEqualTo(1)
    }

    private fun saveFailure(): Long {
        instanceA.saveDLQMessage(
            DeadLetterMessage(
                originalTopic = "payment",
                originalPartition = 0,
                originalOffset = 1L,
                originalKey = "200",
                originalValue = """{"eventType":"PaymentCompleted","orderId":200}""",
                exception = "java.lang.IllegalStateException: 처리 실패",
                timestamp = System.currentTimeMillis()
            )
        )
        return dlqMessageRepository.findAll().single().id!!
    }

    class BlockingPublisher : DLQMessagePublisher {
        val published = CopyOnWriteArrayList<String>()
        var firstEntered = CountDownLatch(1)
        var releaseFirst = CountDownLatch(1)
        var secondPublished = CountDownLatch(1)
        var firstResult = true

        override fun publish(topic: String, key: String, value: Any?): Boolean {
            if (firstEntered.count > 0) {
                firstEntered.countDown()
                releaseFirst.await(10, TimeUnit.SECONDS)
                published.add(key)
                return firstResult
            }
            published.add(key)
            secondPublished.countDown()
            return true
        }

        fun reset() {
            published.clear()
            firstEntered = CountDownLatch(1)
            releaseFirst = CountDownLatch(1)
            secondPublished = CountDownLatch(1)
            firstResult = true
        }
    }

    @Configuration
    @EntityScan(basePackageClasses = [DLQMessage::class])
    @EnableJpaRepositories(basePackageClasses = [DLQMessageRepository::class])
    class TestConfig {

        @Bean
        fun blockingPublisher(): BlockingPublisher = BlockingPublisher()

        @Bean
        fun instanceA(repository: DLQMessageRepository, publisher: BlockingPublisher): DLQCommandService =
            DLQCommandService(repository, publisher, NoOpDLQMetrics(), NoOpDLQArchivalService())

        @Bean
        fun instanceB(repository: DLQMessageRepository, publisher: BlockingPublisher): DLQCommandService =
            DLQCommandService(repository, publisher, NoOpDLQMetrics(), NoOpDLQArchivalService())
    }
}
