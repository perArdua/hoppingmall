package com.hoppingmall.outbox.scheduler

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.metrics.OutboxMetrics
import com.hoppingmall.outbox.repository.OutboxEventRepository
import com.hoppingmall.outbox.service.OutboxEventWriter
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

@DisplayName("MySQL이 아닌 DB(H2)에서의 outbox 발행")
@DisplayNameGeneration(ReplaceUnderscores::class)
@DataJpaTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:outbox-h2;DB_CLOSE_DELAY=-1;MODE=MYSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    ]
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes = [OutboxPublishH2Test.TestConfig::class])
class OutboxPublishH2Test {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Autowired
    private lateinit var outboxEventPublisher: OutboxEventPublisher

    @Autowired
    private lateinit var outboxEventWriter: OutboxEventWriter

    private lateinit var appender: ListAppender<ILoggingEvent>

    @BeforeEach
    fun setUp() {
        sentKeys.clear()
        appender = ListAppender<ILoggingEvent>().apply { start() }
        loggers().forEach { it.addAppender(appender) }
    }

    @AfterEach
    fun tearDown() {
        loggers().forEach { it.detachAppender(appender) }
        appender.stop()
        jdbcTemplate.update("DELETE FROM outbox_events")
    }

    @Test
    fun 기본_설정이면_기동_판별로_일괄_선점이_꺼지고_행_단위_선점으로_오류_없이_발행한다() {
        insertPending(150)

        outboxEventPublisher.publishPendingEvents()
        val afterFirstRun = countProcessed()
        outboxEventPublisher.publishPendingEvents()

        assertThat(ReflectionTestUtils.getField(outboxEventPublisher, "batchClaim")).isEqualTo(false)
        assertThat(afterFirstRun).isEqualTo(100L)
        assertThat(countProcessed()).isEqualTo(150L)
        assertThat(outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED)).isEqualTo(150L)
        assertThat(sentKeys).hasSize(150)
        assertThat(problemLogs()).isEmpty()
    }

    @Test
    fun 기본_설정이면_쓴_이벤트를_커밋_뒤_깨우기로_오류_없이_발행한다() {
        outboxEventWriter.saveEvent(
            aggregateType = "Payment",
            aggregateId = "42",
            eventType = "PaymentCompleted",
            eventData = mapOf("paymentId" to 42),
            topic = "payment"
        )

        val deadline = System.currentTimeMillis() + 10_000
        while (countProcessed() < 1L && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }

        assertThat(countProcessed()).isEqualTo(1L)
        assertThat(sentKeys.keys).containsExactly("42")
        assertThat(problemLogs()).isEmpty()
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun 기동_판별은_H2를_읽어_행_단위로_돈다는_한_줄을_남긴다() {
        val publisher = OutboxEventPublisher(
            outboxEventRepository,
            Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<String, Any>,
            Mockito.mock(OutboxMetrics::class.java),
            Mockito.mock(AvroEventConverter::class.java),
            transactionManager,
            OutboxBrokerBackoff(Mockito.mock(OutboxMetrics::class.java)),
            dataSource = dataSource
        )

        publisher.configureClaimMode()

        assertThat(ReflectionTestUtils.getField(publisher, "batchClaim")).isEqualTo(false)
        assertThat(appender.list.map { it.formattedMessage }).contains(
            "Outbox batch claim disabled: connected database is H2, not MySQL; publishing with per-row claims"
        )
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun 판별_없이_일괄_선점을_켜면_H2에서는_잠금_조회_SQL이_실패한다() {
        insertPending(1)
        val publisher = OutboxEventPublisher(
            outboxEventRepository,
            Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<String, Any>,
            Mockito.mock(OutboxMetrics::class.java),
            Mockito.mock(AvroEventConverter::class.java),
            transactionManager,
            OutboxBrokerBackoff(Mockito.mock(OutboxMetrics::class.java))
        )

        assertThatThrownBy { publisher.publishPendingEvents() }
            .hasStackTraceContaining("FORCE")
        assertThat(countProcessed()).isZero()
    }

    private fun insertPending(count: Int) {
        val base = LocalDateTime.now().minusMinutes(1)
        outboxEventRepository.saveAll(
            (1..count).map { i ->
                OutboxEvent(
                    aggregateType = "Payment",
                    aggregateId = i.toString(),
                    eventType = "PaymentCompleted",
                    eventData = """{"paymentId":$i}""",
                    topic = "payment",
                    partitionKey = "k$i",
                    createdAt = base.plusNanos(i * 1000L)
                )
            }
        )
    }

    private fun countProcessed(): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE processed = true", Long::class.java)!!

    private fun problemLogs(): List<String> =
        appender.list.filter { it.level.isGreaterOrEqual(Level.WARN) }.map { it.formattedMessage }

    private fun loggers(): List<Logger> = listOf(
        LoggerFactory.getLogger(OutboxEventPublisher::class.java) as Logger,
        LoggerFactory.getLogger(OutboxPublishWaker::class.java) as Logger,
        LoggerFactory.getLogger("org.hibernate.engine.jdbc.spi.SqlExceptionHelper") as Logger
    )

    @Configuration
    @EntityScan(basePackageClasses = [OutboxEvent::class])
    @EnableJpaRepositories(basePackageClasses = [OutboxEventRepository::class])
    @Import(OutboxEventPublisher::class, OutboxBrokerBackoff::class, OutboxPublishWaker::class, OutboxEventWriter::class)
    class TestConfig {

        @Bean
        fun objectMapper(): ObjectMapper = ObjectMapper()

        @Bean
        fun outboxMetrics(): OutboxMetrics = Mockito.mock(OutboxMetrics::class.java)

        @Bean
        fun avroEventConverter(): AvroEventConverter {
            val converter = Mockito.mock(AvroEventConverter::class.java)
            whenever(converter.convertJsonToAvro(any(), any())).thenReturn(Mockito.mock(GenericRecord::class.java))
            return converter
        }

        @Bean
        @Suppress("UNCHECKED_CAST")
        fun kafkaTemplate(): KafkaTemplate<String, Any> {
            val template = Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<String, Any>
            whenever(template.send(any<String>(), any<String>(), any())).thenAnswer { invocation ->
                val topic = invocation.arguments[0] as String
                val key = invocation.arguments[1] as String
                sentKeys.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
                CompletableFuture.completedFuture(
                    SendResult<String, Any>(
                        ProducerRecord(topic, key, invocation.arguments[2]),
                        RecordMetadata(TopicPartition(topic, 0), 0L, 0, System.currentTimeMillis(), 0, 0)
                    )
                )
            }
            whenever(template.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>()))
                .thenAnswer { invocation ->
                    (invocation.arguments[0] as KafkaOperations.OperationsCallback<String, Any, Any>)
                        .doInOperations(template)
                }
            return template
        }
    }

    companion object {
        private val sentKeys = ConcurrentHashMap<String, AtomicInteger>()
    }
}
