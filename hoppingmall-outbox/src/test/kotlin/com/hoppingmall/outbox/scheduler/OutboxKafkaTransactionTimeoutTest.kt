package com.hoppingmall.outbox.scheduler

import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.ProducerFencedException
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@DisplayName("Kafka 트랜잭션 제한 시간의 브로커 동작")
@DisplayNameGeneration(ReplaceUnderscores::class)
@EnabledIfEnvironmentVariable(named = "OUTBOX_KAFKA_TX", matches = "true")
class OutboxKafkaTransactionTimeoutTest {

    @RepeatedTest(2)
    fun 첫_전송_뒤_제한_시간을_넘겨_커밋하면_커밋이_실패하고_기록은_버려진다() {
        val topic = createTopic(fastCleanupKafka)
        val transactionalId = "outbox-tx-${UUID.randomUUID()}"
        val producer = transactionalProducer(fastCleanupKafka, transactionalId)
        producer.initTransactions()
        producer.beginTransaction()
        producer.send(ProducerRecord(topic, "key", "expired")).get(10, TimeUnit.SECONDS)
        Thread.sleep(TRANSACTION_TIMEOUT_MS + FAST_CLEANUP_INTERVAL_MS + MARGIN_MS)

        val commitFailure = catchThrowable { producer.commitTransaction() }
        report("scene1 commit", commitFailure)
        val abortFailure = catchThrowable { producer.abortTransaction() }
        report("scene1 abort after failed commit", abortFailure)
        val reuseFailure = catchThrowable {
            producer.beginTransaction()
            producer.send(ProducerRecord(topic, "key", "same-producer")).get(10, TimeUnit.SECONDS)
            producer.commitTransaction()
        }
        report("scene1 same producer next transaction", reuseFailure)
        producer.close(Duration.ofSeconds(5))

        transactionalProducer(fastCleanupKafka, transactionalId).use { fresh ->
            fresh.initTransactions()
            fresh.beginTransaction()
            fresh.send(ProducerRecord(topic, "key", "fresh-producer")).get(10, TimeUnit.SECONDS)
            fresh.commitTransaction()
        }

        val committed = readAll(fastCleanupKafka, topic, "read_committed")
        val uncommitted = readAll(fastCleanupKafka, topic, "read_uncommitted")
        println("[KAFKA-TX] scene1 read_committed=$committed read_uncommitted=$uncommitted")

        assertThat(commitFailure).isInstanceOf(ProducerFencedException::class.java)
        assertThat(abortFailure).isInstanceOf(ProducerFencedException::class.java)
        assertThat(reuseFailure).isInstanceOf(ProducerFencedException::class.java)
        assertThat(committed).containsExactly("fresh-producer")
        assertThat(uncommitted).containsExactly("expired", "fresh-producer")
    }

    @RepeatedTest(2)
    fun 전송_없이_제한_시간을_넘긴_뒤_첫_전송과_커밋은_성공한다() {
        val topic = createTopic(fastCleanupKafka)
        val transactionalId = "outbox-tx-${UUID.randomUUID()}"
        val commitFailure = transactionalProducer(fastCleanupKafka, transactionalId).use { producer ->
            producer.initTransactions()
            producer.beginTransaction()
            Thread.sleep(TRANSACTION_TIMEOUT_MS + FAST_CLEANUP_INTERVAL_MS + MARGIN_MS)
            catchThrowable {
                producer.send(ProducerRecord(topic, "key", "late-first-send")).get(10, TimeUnit.SECONDS)
                producer.commitTransaction()
            }
        }
        report("scene3 late first send and commit", commitFailure)

        val committed = readAll(fastCleanupKafka, topic, "read_committed")
        println("[KAFKA-TX] scene3 read_committed=$committed")

        assertThat(commitFailure).isNull()
        assertThat(committed).containsExactly("late-first-send")
    }

    @RepeatedTest(2)
    fun 제한_시간이_지나도_브로커_정리_주기가_돌기_전에_커밋하면_성공한다() {
        val topic = createTopic(slowCleanupKafka)
        val transactionalId = "outbox-tx-${UUID.randomUUID()}"
        val commitFailure = transactionalProducer(slowCleanupKafka, transactionalId).use { producer ->
            producer.initTransactions()
            producer.beginTransaction()
            producer.send(ProducerRecord(topic, "key", "past-timeout")).get(10, TimeUnit.SECONDS)
            Thread.sleep(TRANSACTION_TIMEOUT_MS + MARGIN_MS)
            catchThrowable { producer.commitTransaction() }
        }
        report("scene1b commit before cleanup tick", commitFailure)

        val committed = readAll(slowCleanupKafka, topic, "read_committed")
        println("[KAFKA-TX] scene1b read_committed=$committed")

        assertThat(commitFailure).isNull()
        assertThat(committed).containsExactly("past-timeout")
    }

    @RepeatedTest(2)
    fun 템플릿_트랜잭션_콜백_안에서_제한_시간을_넘기면_커밋_예외가_콜백_밖으로_나온다() {
        val topic = createTopic(fastCleanupKafka)
        val producerFactory = DefaultKafkaProducerFactory<String, String>(
            producerProperties(fastCleanupKafka) - ProducerConfig.TRANSACTIONAL_ID_CONFIG
        ).apply { setTransactionIdPrefix("outbox-tpl-${UUID.randomUUID()}-") }
        val template = KafkaTemplate(producerFactory)
        try {
            val failure = catchThrowable {
                template.executeInTransaction { operations ->
                    operations.send(topic, "key", "template-expired").get(10, TimeUnit.SECONDS)
                        .also { Thread.sleep(TRANSACTION_TIMEOUT_MS + FAST_CLEANUP_INTERVAL_MS + MARGIN_MS) }
                }
            }
            report("template scene1 executeInTransaction", failure)

            val nextFailure = catchThrowable {
                template.executeInTransaction { operations ->
                    operations.send(topic, "key", "template-next").get(10, TimeUnit.SECONDS)
                }
            }
            report("template next executeInTransaction", nextFailure)

            val committed = readAll(fastCleanupKafka, topic, "read_committed")
            val uncommitted = readAll(fastCleanupKafka, topic, "read_uncommitted")
            println("[KAFKA-TX] template read_committed=$committed read_uncommitted=$uncommitted")

            assertThat(failure).isInstanceOf(ProducerFencedException::class.java)
            assertThat(nextFailure).isNull()
            assertThat(committed).containsExactly("template-next")
            assertThat(uncommitted).containsExactly("template-expired", "template-next")
        } finally {
            producerFactory.destroy()
        }
    }

    private fun report(label: String, failure: Throwable?) {
        if (failure == null) {
            println("[KAFKA-TX] $label -> success")
            return
        }
        val chain = generateSequence(failure) { it.cause }
            .joinToString(" <- ") { "${it.javaClass.name}: ${it.message}" }
        println("[KAFKA-TX] $label -> $chain")
    }

    private fun createTopic(kafka: ConfluentKafkaContainer): String {
        val topic = "outbox-tx-${UUID.randomUUID()}"
        Admin.create(mapOf<String, Any>(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers)).use { admin ->
            admin.createTopics(listOf(NewTopic(topic, 1, 1.toShort()))).all().get(30, TimeUnit.SECONDS)
        }
        return topic
    }

    private fun producerProperties(kafka: ConfluentKafkaContainer): Map<String, Any> = mapOf(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
        ProducerConfig.ACKS_CONFIG to "all",
        ProducerConfig.TRANSACTION_TIMEOUT_CONFIG to TRANSACTION_TIMEOUT_MS.toInt(),
        ProducerConfig.TRANSACTIONAL_ID_CONFIG to "unused"
    )

    private fun transactionalProducer(kafka: ConfluentKafkaContainer, transactionalId: String): KafkaProducer<String, String> =
        KafkaProducer(producerProperties(kafka) + (ProducerConfig.TRANSACTIONAL_ID_CONFIG to transactionalId))

    private fun readAll(kafka: ConfluentKafkaContainer, topic: String, isolation: String): List<String> {
        val partition = TopicPartition(topic, 0)
        val properties = mapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.ISOLATION_LEVEL_CONFIG to isolation
        )
        return KafkaConsumer<String, String>(properties).use { consumer ->
            consumer.assign(listOf(partition))
            consumer.seekToBeginning(listOf(partition))
            val end = consumer.endOffsets(listOf(partition)).getValue(partition)
            val logEnd = KafkaConsumer<String, String>(properties + (ConsumerConfig.ISOLATION_LEVEL_CONFIG to "read_uncommitted"))
                .use { it.endOffsets(listOf(partition)).getValue(partition) }
            assertThat(end).isEqualTo(logEnd)
            val values = mutableListOf<String>()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (consumer.position(partition) < end && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(200)).forEach { values += it.value() }
            }
            assertThat(consumer.position(partition)).isGreaterThanOrEqualTo(end)
            values
        }
    }

    companion object {
        private const val TRANSACTION_TIMEOUT_MS = 5_000L
        private const val FAST_CLEANUP_INTERVAL_MS = 1_000L
        private const val SLOW_CLEANUP_INTERVAL_MS = 600_000L
        private const val MARGIN_MS = 3_000L
        private val IMAGE = DockerImageName.parse("confluentinc/cp-kafka:7.6.0")

        private val fastCleanupKafka: ConfluentKafkaContainer by lazy {
            ConfluentKafkaContainer(IMAGE)
                .withEnv("KAFKA_TRANSACTION_ABORT_TIMED_OUT_TRANSACTION_CLEANUP_INTERVAL_MS", FAST_CLEANUP_INTERVAL_MS.toString())
                .also { it.start() }
        }

        private val slowCleanupKafka: ConfluentKafkaContainer by lazy {
            ConfluentKafkaContainer(IMAGE)
                .withEnv("KAFKA_TRANSACTION_ABORT_TIMED_OUT_TRANSACTION_CLEANUP_INTERVAL_MS", SLOW_CLEANUP_INTERVAL_MS.toString())
                .also { it.start() }
        }
    }
}
