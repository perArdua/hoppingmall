package com.hoppingmall.dlq.service

import com.hoppingmall.dlq.archival.DLQArchivalService
import com.hoppingmall.dlq.archival.NoOpDLQArchivalService
import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import com.hoppingmall.dlq.dto.DLQRetryResultResponse
import com.hoppingmall.dlq.metrics.DLQMetrics
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
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.MySQLContainer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("관리자 DLQ 일괄 재처리의 트랜잭션 범위와 건수 상한 (MySQL)")
@DisplayNameGeneration(ReplaceUnderscores::class)
@DataJpaTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "dlq.admin.bulk-retry-max-count=3"
    ]
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes = [DLQBulkRetryMySqlTransactionTest.TestConfig::class])
class DLQBulkRetryMySqlTransactionTest {

    @Autowired
    private lateinit var dlqCommandService: DLQCommandService

    @Autowired
    private lateinit var dlqMessageRepository: DLQMessageRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var publisher: SecondPublishBlockingPublisher

    private val executor = Executors.newSingleThreadExecutor()

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM dlq_messages")
        publisher.reset()
    }

    @AfterEach
    fun tearDown() {
        publisher.releaseSecond.countDown()
        executor.shutdownNow()
        jdbcTemplate.update("DELETE FROM dlq_messages")
    }

    @Test
    fun 일괄_재처리는_메시지마다_커밋해_다음_메시지를_처리하는_동안_앞_메시지의_행을_잠그지_않는다() {
        val ids = savePending(3)

        val batch = executor.submit<DLQRetryResultResponse> { dlqCommandService.retryDLQMessagesByTopic(TOPIC, 50) }
        assertThat(publisher.secondEntered.await(15, TimeUnit.SECONDS)).isTrue()

        val lockedIds = ids.filter { isRowLocked(it) }
        val processedVisible = countByStatus(DLQStatus.PROCESSED)
        publisher.releaseSecond.countDown()
        val result = batch.get(15, TimeUnit.SECONDS)

        assertThat(lockedIds).hasSize(1)
        assertThat(processedVisible).isEqualTo(1L)
        assertThat(result.successCount).isEqualTo(3)
        assertThat(countByStatus(DLQStatus.PROCESSED)).isEqualTo(3L)
    }

    @Test
    fun 일괄_재처리는_요청한_maxCount가_상한을_넘으면_상한_건수까지만_재처리한다() {
        savePending(5)
        publisher.blockSecond = false

        val result = dlqCommandService.retryDLQMessagesByTopic(TOPIC, 50)

        assertThat(result.totalAttempted).isEqualTo(3)
        assertThat(countByStatus(DLQStatus.PROCESSED)).isEqualTo(3L)
        assertThat(countByStatus(DLQStatus.PENDING)).isEqualTo(2L)
    }

    @Test
    fun 일괄_재처리는_maxCount가_0이면_아무것도_재처리하지_않는다() {
        savePending(2)
        publisher.blockSecond = false

        val result = dlqCommandService.retryDLQMessagesByTopic(TOPIC, 0)

        assertThat(result.totalAttempted).isEqualTo(0)
        assertThat(countByStatus(DLQStatus.PENDING)).isEqualTo(2L)
    }

    private fun savePending(count: Int): List<Long> =
        dlqMessageRepository.saveAll((1..count).map { message(it.toLong()) }).map { it.id!! }

    private fun isRowLocked(id: Long): Boolean =
        try {
            jdbcTemplate.queryForList("SELECT id FROM dlq_messages WHERE id = ? FOR UPDATE NOWAIT", id)
            false
        } catch (e: DataAccessException) {
            true
        }

    private fun countByStatus(status: DLQStatus): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dlq_messages WHERE status = ?", Long::class.java, status.name
        )!!

    private fun message(offset: Long): DLQMessage =
        DLQMessage(
            originalTopic = TOPIC,
            originalPartition = 0,
            originalOffset = offset,
            originalKey = "key-$offset",
            originalValue = """{"eventType":"PaymentCompleted","orderId":$offset}""",
            exceptionMessage = "java.lang.IllegalStateException: 처리 실패",
            errorTimestamp = System.currentTimeMillis(),
            nextRetryAt = System.currentTimeMillis()
        )

    class SecondPublishBlockingPublisher : DLQMessagePublisher {
        private val calls = AtomicInteger()
        var secondEntered = CountDownLatch(1)
        var releaseSecond = CountDownLatch(1)
        @Volatile
        var blockSecond = true

        override fun publish(topic: String, key: String, value: Any?): Boolean {
            if (calls.incrementAndGet() == 2 && blockSecond) {
                secondEntered.countDown()
                releaseSecond.await(15, TimeUnit.SECONDS)
            }
            return true
        }

        fun reset() {
            calls.set(0)
            secondEntered = CountDownLatch(1)
            releaseSecond = CountDownLatch(1)
            blockSecond = true
        }
    }

    @Configuration
    @EntityScan(basePackageClasses = [DLQMessage::class])
    @EnableJpaRepositories(basePackageClasses = [DLQMessageRepository::class])
    @Import(DLQCommandService::class)
    class TestConfig {

        @Bean
        fun secondPublishBlockingPublisher(): SecondPublishBlockingPublisher = SecondPublishBlockingPublisher()

        @Bean
        fun dlqMetrics(): DLQMetrics = NoOpDLQMetrics()

        @Bean
        fun dlqArchivalService(): DLQArchivalService = NoOpDLQArchivalService()
    }

    companion object {
        private const val TOPIC = "payment"

        private val mysql: MySQLContainer<*> by lazy {
            val container: MySQLContainer<*> = MySQLContainer("mysql:8.0.44")
            container.withUsername("root")
            container.also { it.start() }
        }

        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
            registry.add("spring.jpa.database-platform") { "org.hibernate.dialect.MySQLDialect" }
            registry.add("spring.datasource.hikari.maximum-pool-size") { 4 }
            registry.add("spring.datasource.hikari.minimum-idle") { 0 }
        }
    }
}
