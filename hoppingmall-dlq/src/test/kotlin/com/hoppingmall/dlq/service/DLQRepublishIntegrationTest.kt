package com.hoppingmall.dlq.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.common.event.AvroJsonDeserializer
import com.hoppingmall.dlq.archival.DLQArchivalService
import com.hoppingmall.dlq.archival.NoOpDLQArchivalService
import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.DeadLetterMessage
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import com.hoppingmall.dlq.metrics.DLQMetrics
import com.hoppingmall.dlq.metrics.NoOpDLQMetrics
import com.hoppingmall.dlq.publisher.DLQMessagePublisher
import com.hoppingmall.dlq.publisher.KafkaDLQMessagePublisher
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.repository.findByIdOrNull
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.mock.MockProducerFactory
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CopyOnWriteArrayList

@DisplayName("DLQ 재발행 통합")
@DisplayNameGeneration(ReplaceUnderscores::class)
@DataJpaTest
@ContextConfiguration(classes = [DLQRepublishIntegrationTest.TestConfig::class])
@Import(DLQCommandService::class, DLQScheduler::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DLQRepublishIntegrationTest {

    @Autowired
    private lateinit var dlqCommandService: DLQCommandService

    @Autowired
    private lateinit var dlqScheduler: DLQScheduler

    @Autowired
    private lateinit var dlqMessageRepository: DLQMessageRepository

    @Autowired
    private lateinit var producers: TestProducers

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        dlqMessageRepository.deleteAll()
        producers.reset()
    }

    @AfterEach
    fun tearDown() {
        MockSchemaRegistry.dropScope(SCHEMA_SCOPE)
    }

    @Nested
    @DisplayName("재발행 형식")
    inner class RepublishFormat {

        @Test
        fun 결제_취소_요청을_원래_Avro_형식으로_재발행한다() {
            val consumed = consumedJson(
                KafkaTopics.PAYMENT_COMPENSATION,
                "PaymentCancellationRequested",
                mapOf("eventId" to "cancel-req-1", "orderId" to 200L)
            )
            val id = saveFailure(KafkaTopics.PAYMENT_COMPENSATION, 10L, "200", consumed)

            val result = dlqCommandService.retryDLQMessage(id)

            assertThat(result).isTrue()
            assertThat(row(id).status).isEqualTo(DLQStatus.PROCESSED)
            val sent = producers.committed().single()
            assertThat(sent.topic()).isEqualTo(KafkaTopics.PAYMENT_COMPENSATION)
            assertThat(sent.key()).isEqualTo("200")
            assertThat(sent.value()).isInstanceOf(GenericRecord::class.java)
            assertThat((sent.value() as GenericRecord).get("eventType").toString()).isEqualTo("PaymentCancellationRequested")
            assertThat(objectMapper.readTree(readBack(sent))).isEqualTo(objectMapper.readTree(consumed))
        }

        @Test
        fun 결제_완료_이벤트를_원래_Avro_형식으로_재발행한다() {
            val consumed = consumedJson(
                KafkaTopics.PAYMENT,
                "PaymentCompleted",
                mapOf(
                    "paymentId" to 100L,
                    "orderId" to 200L,
                    "userId" to 1L,
                    "amount" to "15000",
                    "pointAmount" to "0",
                    "method" to "CREDIT_CARD",
                    "status" to "SUCCESS",
                    "transactionId" to "tx-1",
                    "completedAt" to "2026-11-13T10:00:00"
                )
            )
            val id = saveFailure(KafkaTopics.PAYMENT, 11L, "200", consumed)

            val result = dlqCommandService.retryDLQMessage(id)

            assertThat(result).isTrue()
            assertThat(row(id).status).isEqualTo(DLQStatus.PROCESSED)
            val sent = producers.committed().single()
            assertThat(sent.value()).isInstanceOf(GenericRecord::class.java)
            assertThat((sent.value() as GenericRecord).get("eventType").toString()).isEqualTo("PaymentCompleted")
            assertThat(objectMapper.readTree(readBack(sent))).isEqualTo(objectMapper.readTree(consumed))
        }

        @Test
        fun eventType이_없는_값은_재발행하지_않고_FAILED로_둔다() {
            val id = saveFailure(KafkaTopics.PAYMENT_COMPENSATION, 12L, "200", """{"orderId":200}""")

            val result = dlqCommandService.retryDLQMessage(id)

            assertThat(result).isFalse()
            val saved = row(id)
            assertThat(saved.status).isEqualTo(DLQStatus.FAILED)
            assertThat(saved.notes).contains("eventType")
            assertThat(producers.committed()).isEmpty()
        }

        @Test
        fun 알_수_없는_eventType은_재발행하지_않고_FAILED로_둔다() {
            val id = saveFailure(
                KafkaTopics.PAYMENT_COMPENSATION,
                13L,
                "200",
                """{"eventType":"SomethingUnknown","orderId":200}"""
            )

            val result = dlqCommandService.retryDLQMessage(id)

            assertThat(result).isFalse()
            val saved = row(id)
            assertThat(saved.status).isEqualTo(DLQStatus.FAILED)
            assertThat(saved.notes).contains("SomethingUnknown")
            assertThat(producers.committed()).isEmpty()
        }
    }

    @Nested
    @DisplayName("재시도 한도")
    inner class RetryLimit {

        @Test
        fun 재발행이_세_번째에도_실패하면_FAILED가_된다() {
            val id = saveFailure(KafkaTopics.PAYMENT_COMPENSATION, 20L, "200", cancellationRequested("cancel-req-20"))
            update(id) { it.retryCount = 2 }
            producers.sendFailure = KafkaException("broker unavailable")

            val result = dlqCommandService.retryDLQMessage(id)

            assertThat(result).isFalse()
            val saved = row(id)
            assertThat(saved.retryCount).isEqualTo(3)
            assertThat(saved.status).isEqualTo(DLQStatus.FAILED)
        }

        @Test
        fun 재시도를_다_쓰고_PENDING에_남은_행은_스케줄러가_FAILED로_옮긴다() {
            val id = saveFailure(KafkaTopics.PAYMENT_COMPENSATION, 21L, "200", cancellationRequested("cancel-req-21"))
            update(id) {
                it.retryCount = 3
                it.nextRetryAt = System.currentTimeMillis() - 1_000
            }

            dlqScheduler.autoRetryPendingMessages()

            assertThat(row(id).status).isEqualTo(DLQStatus.FAILED)
        }

        @Test
        fun Kafka_커밋이_실패하면_PROCESSED로_기록하지_않는다() {
            producers.avroValues = false
            producers.commitFailure = KafkaException("commit failed")
            val id = saveFailure(KafkaTopics.PAYMENT_COMPENSATION, 22L, "200", cancellationRequested("cancel-req-22"))

            runCatching { dlqCommandService.retryDLQMessage(id) }

            assertThat(row(id).status).isEqualTo(DLQStatus.PENDING)
            assertThat(producers.committed()).isEmpty()
        }
    }

    @Nested
    @DisplayName("재발행 뒤 다시 실패")
    inner class FailedAgainAfterRepublish {

        @Test
        fun 재발행한_메시지가_다시_실패하면_새_행을_만들지_않고_기존_행을_다시_PENDING으로_돌린다() {
            val value = cancellationRequested("cancel-req-30")
            val id = saveFailure(KafkaTopics.PAYMENT_COMPENSATION, 30L, "200", value)
            update(id) {
                it.retryCount = 1
                it.markAsProcessed("재처리 성공")
            }

            saveFailure(KafkaTopics.PAYMENT_COMPENSATION, 31L, "200", value)

            val rows = dlqMessageRepository.findAll()
            assertThat(rows).hasSize(1)
            assertThat(rows.single().id).isEqualTo(id)
            assertThat(rows.single().status).isEqualTo(DLQStatus.PENDING)
            assertThat(rows.single().retryCount).isEqualTo(1)
            assertThat(rows.single().nextRetryAt).isNotNull()
        }

        @Test
        fun 재시도를_다_쓴_뒤_다시_실패하면_FAILED가_된다() {
            val value = cancellationRequested("cancel-req-31")
            val id = saveFailure(KafkaTopics.PAYMENT_COMPENSATION, 32L, "200", value)
            update(id) {
                it.retryCount = 3
                it.markAsProcessed("재처리 성공")
            }

            saveFailure(KafkaTopics.PAYMENT_COMPENSATION, 33L, "200", value)

            val rows = dlqMessageRepository.findAll()
            assertThat(rows).hasSize(1)
            assertThat(rows.single().status).isEqualTo(DLQStatus.FAILED)
        }

        @Test
        fun 같은_내용이_아직_PENDING이면_새_행을_만들지_않는다() {
            val value = cancellationRequested("cancel-req-32")
            saveFailure(KafkaTopics.PAYMENT_COMPENSATION, 34L, "200", value)

            saveFailure(KafkaTopics.PAYMENT_COMPENSATION, 35L, "200", value)

            assertThat(dlqMessageRepository.findAll()).hasSize(1)
        }

        @Test
        fun 내용이_다른_메시지는_새_행으로_저장한다() {
            saveFailure(KafkaTopics.PAYMENT_COMPENSATION, 36L, "200", cancellationRequested("cancel-req-33"))

            saveFailure(KafkaTopics.PAYMENT_COMPENSATION, 37L, "201", cancellationRequested("cancel-req-34"))

            assertThat(dlqMessageRepository.findAll()).hasSize(2)
        }

        @Test
        fun 계속_실패하는_메시지는_세_번_재발행한_뒤_FAILED에서_멈춘다() {
            val value = cancellationRequested("cancel-req-35")
            var offset = 100L
            saveFailure(KafkaTopics.PAYMENT_COMPENSATION, offset, "200", value)

            repeat(4) {
                dlqMessageRepository.findAll()
                    .filter { it.status == DLQStatus.PENDING }
                    .forEach { dlqCommandService.retryDLQMessage(it.id!!) }
                offset++
                saveFailure(KafkaTopics.PAYMENT_COMPENSATION, offset, "200", value)
            }

            val rows = dlqMessageRepository.findAll()
            assertThat(rows).hasSize(1)
            assertThat(rows.single().status).isEqualTo(DLQStatus.FAILED)
            assertThat(rows.single().retryCount).isEqualTo(3)
            assertThat(producers.committed()).hasSize(3)
        }
    }

    private fun saveFailure(topic: String, offset: Long, key: String?, value: String): Long {
        dlqCommandService.saveDLQMessage(
            DeadLetterMessage(
                originalTopic = topic,
                originalPartition = 0,
                originalOffset = offset,
                originalKey = key,
                originalValue = value,
                exception = "java.lang.IllegalStateException: 처리 실패",
                timestamp = System.currentTimeMillis()
            )
        )
        return dlqMessageRepository.findAll().maxOf { it.id!! }
    }

    private fun row(id: Long): DLQMessage = dlqMessageRepository.findByIdOrNull(id)!!

    private fun update(id: Long, change: (DLQMessage) -> Unit) {
        val message = row(id)
        change(message)
        dlqMessageRepository.save(message)
    }

    private fun cancellationRequested(eventId: String): String = consumedJson(
        KafkaTopics.PAYMENT_COMPENSATION,
        "PaymentCancellationRequested",
        mapOf("eventId" to eventId, "orderId" to 200L)
    )

    private fun consumedJson(topic: String, eventType: String, fields: Map<String, Any>): String {
        val json = objectMapper.writeValueAsString(fields + ("eventType" to eventType))
        val record = AvroEventConverter(objectMapper).convertJsonToAvro(eventType, json)
        return AvroJsonDeserializer().apply { configure(serdeConfig(), false) }
            .deserialize(topic, avroSerializer().serialize(topic, record))!!
    }

    private fun readBack(record: ProducerRecord<String, Any>): String =
        AvroJsonDeserializer().apply { configure(serdeConfig(), false) }
            .deserialize(record.topic(), avroSerializer().serialize(record.topic(), record.value()))!!

    class TestProducers {
        val created = CopyOnWriteArrayList<MockProducer<String, Any>>()
        var avroValues = true
        var sendFailure: RuntimeException? = null
        var commitFailure: RuntimeException? = null

        fun create(): MockProducer<String, Any> {
            val valueSerializer: Serializer<Any> =
                if (avroValues) avroSerializer() else Serializer { _, _ -> ByteArray(0) }
            return MockProducer(true, StringSerializer(), valueSerializer).also {
                it.initTransactions()
                it.sendException = sendFailure
                it.commitTransactionException = commitFailure
                created.add(it)
            }
        }

        fun committed(): List<ProducerRecord<String, Any>> = created.flatMap { it.history() }

        fun reset() {
            created.clear()
            avroValues = true
            sendFailure = null
            commitFailure = null
        }
    }

    @Configuration
    @EntityScan(basePackageClasses = [DLQMessage::class])
    @EnableJpaRepositories(basePackageClasses = [DLQMessageRepository::class])
    class TestConfig {

        @Bean
        fun testProducers(): TestProducers = TestProducers()

        @Bean
        fun kafkaTemplate(testProducers: TestProducers): KafkaTemplate<String, Any> =
            KafkaTemplate(MockProducerFactory<String, Any>({ _, _ -> testProducers.create() }, "dlq-test-tx"))

        @Bean
        fun dlqMessagePublisher(kafkaTemplate: KafkaTemplate<String, Any>): DLQMessagePublisher =
            KafkaDLQMessagePublisher(kafkaTemplate)

        @Bean
        fun avroEventConverter(): AvroEventConverter = AvroEventConverter(ObjectMapper())

        @Bean
        fun dlqMetrics(): DLQMetrics = NoOpDLQMetrics()

        @Bean
        fun dlqArchivalService(): DLQArchivalService = NoOpDLQArchivalService()
    }

    companion object {
        private const val SCHEMA_SCOPE = "dlq-republish"

        private fun serdeConfig(): Map<String, Any> = mapOf(
            "schema.registry.url" to "mock://$SCHEMA_SCOPE",
            "value.subject.name.strategy" to TopicRecordNameStrategy::class.java.name
        )

        private fun avroSerializer(): Serializer<Any> =
            KafkaAvroSerializer().apply { configure(serdeConfig(), false) }
    }
}
