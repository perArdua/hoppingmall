package com.hoppingmall.outbox.scheduler

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.hoppingmall.common.event.AvroEventConverter
import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.metrics.OutboxMetrics
import com.hoppingmall.outbox.repository.OutboxEventRepository
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.RecordTooLargeException
import org.apache.kafka.common.errors.TimeoutException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.SendResult
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.SQLException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.random.Random

@DisplayName("OutboxEventPublisher")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OutboxEventPublisherTest {

    @Mock
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Mock
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Mock
    private lateinit var outboxMetrics: OutboxMetrics

    @Mock
    private lateinit var avroEventConverter: AvroEventConverter

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var outboxEventPublisher: OutboxEventPublisher

    @BeforeEach
    fun setUp() {
        Mockito.lenient().`when`(transactionManager.getTransaction(any())).thenReturn(SimpleTransactionStatus())
        outboxEventPublisher = OutboxEventPublisher(
            outboxEventRepository, kafkaTemplate, outboxMetrics, avroEventConverter, transactionManager,
            OutboxBrokerBackoff(outboxMetrics)
        )
    }

    private fun createOutboxEvent(
        id: Long = 1L,
        status: OutboxStatus = OutboxStatus.RETRYING,
        retryCount: Int = 0,
        createdAt: LocalDateTime = LocalDateTime.now(),
        partitionKey: String = "1"
    ): OutboxEvent {
        val event = OutboxEvent(
            aggregateType = "Payment",
            aggregateId = "1",
            eventType = "PaymentCompleted",
            eventData = """{"paymentId":1}""",
            topic = "payment",
            partitionKey = partitionKey,
            status = status,
            createdAt = createdAt
        )
        ReflectionTestUtils.setField(event, "id", id)
        if (retryCount > 0) {
            ReflectionTestUtils.setField(event, "retryCount", retryCount)
        }
        return event
    }

    private fun mockKafkaTransaction(@Suppress("UNUSED_PARAMETER") sendResult: SendResult<String, Any>) {
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, SendResult<String, Any>>>())
        ).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback =
                invocation.arguments[0] as KafkaOperations.OperationsCallback<String, Any, SendResult<String, Any>>
            callback.doInOperations(kafkaTemplate)
        }
    }

    private fun createSendResult(topic: String, key: String, avroRecord: Any): SendResult<String, Any> {
        return SendResult(
            ProducerRecord(topic, key, avroRecord),
            RecordMetadata(TopicPartition(topic, 0), 5L, 0, System.currentTimeMillis(), 0, 0)
        )
    }

    @Test
    fun 발행_성공_시_PUBLISHED_상태로_저장한다() {
        val event = createOutboxEvent()
        val avroRecord = mock<GenericRecord>()
        val sendResult = createSendResult("payment", "1", avroRecord)

        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event))
        whenever(avroEventConverter.convertJsonToAvro(event.eventType, event.eventData)).thenReturn(avroRecord)
        whenever(kafkaTemplate.send("payment", "1", avroRecord))
            .thenReturn(CompletableFuture.completedFuture(sendResult))
        mockKafkaTransaction(sendResult)
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer { it.arguments[0] as OutboxEvent }

        outboxEventPublisher.publishEvent(1L)

        assertThat(event.status).isEqualTo(OutboxStatus.PUBLISHED)
        assertThat(event.processed).isTrue()
        assertThat(event.processedAt).isNotNull()
        verify(outboxEventRepository).save(eq(event))
        verify(outboxMetrics).recordOutboxPublished("payment")
        verify(outboxMetrics).recordPublishLatency(any<LocalDateTime>())
    }

    @Test
    fun 발행_실패_시_재시도_가능하면_FAILED_상태로_저장한다() {
        val event = createOutboxEvent(retryCount = 0)

        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event))
        whenever(avroEventConverter.convertJsonToAvro(event.eventType, event.eventData))
            .thenThrow(RuntimeException("Avro conversion failed"))
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer { it.arguments[0] as OutboxEvent }

        outboxEventPublisher.publishEvent(1L)

        assertThat(event.status).isEqualTo(OutboxStatus.FAILED)
        verify(outboxMetrics).recordOutboxFailed("payment")
        verify(outboxEventRepository).save(eq(event))
    }

    @Test
    fun 최대_재시도_초과_시_영구_실패_처리한다() {
        val event = createOutboxEvent(retryCount = 2)

        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event))
        whenever(avroEventConverter.convertJsonToAvro(event.eventType, event.eventData))
            .thenThrow(RuntimeException("Avro conversion failed"))
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer { it.arguments[0] as OutboxEvent }

        outboxEventPublisher.publishEvent(1L)

        assertThat(event.status).isEqualTo(OutboxStatus.FAILED)
        verify(outboxMetrics).recordOutboxFailed("payment")
        verify(outboxEventRepository).save(eq(event))
    }

    @Test
    fun 이미_처리된_이벤트는_건너뛴다() {
        val event = createOutboxEvent(status = OutboxStatus.RETRYING)
        ReflectionTestUtils.setField(event, "processed", true)

        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event))

        outboxEventPublisher.publishEvent(1L)

        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
        verify(outboxMetrics, never()).recordOutboxPublished(any())
    }

    @Test
    fun RETRYING_상태가_아닌_이벤트는_건너뛴다() {
        val event = createOutboxEvent(status = OutboxStatus.PENDING)

        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event))

        outboxEventPublisher.publishEvent(1L)

        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
        verify(outboxMetrics, never()).recordOutboxPublished(any())
    }

    @Test
    fun 존재하지_않는_이벤트ID는_무시한다() {
        whenever(outboxEventRepository.findById(999L)).thenReturn(Optional.empty())

        outboxEventPublisher.publishEvent(999L)

        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
        verify(outboxMetrics, never()).recordOutboxPublished(any())
    }

    @Test
    fun 대기중인_이벤트가_없으면_아무것도_하지_않는다() {
        ReflectionTestUtils.setField(outboxEventPublisher, "batchClaim", false)
        whenever(
            outboxEventRepository.findUnprocessedEvents(
                status = OutboxStatus.PENDING,
                retryStatus = OutboxStatus.FAILED,
                maxRetries = 3,
                limit = 100
            )
        ).thenReturn(emptyList())

        outboxEventPublisher.publishPendingEvents()

        verify(outboxEventRepository, never()).claimEventForPublish(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun null_id를_가진_이벤트는_클레임하지_않는다() {
        ReflectionTestUtils.setField(outboxEventPublisher, "batchClaim", false)
        val eventWithNullId = OutboxEvent(
            aggregateType = "Payment",
            aggregateId = "1",
            eventType = "PaymentCompleted",
            eventData = """{"paymentId":1}""",
            topic = "payment",
            partitionKey = "1",
            status = OutboxStatus.PENDING
        )

        whenever(
            outboxEventRepository.findUnprocessedEvents(
                status = OutboxStatus.PENDING,
                retryStatus = OutboxStatus.FAILED,
                maxRetries = 3,
                limit = 100
            )
        ).thenReturn(listOf(eventWithNullId))

        outboxEventPublisher.publishPendingEvents()

        verify(outboxEventRepository, never()).claimEventForPublish(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun 클레임_실패한_이벤트는_발행하지_않는다() {
        ReflectionTestUtils.setField(outboxEventPublisher, "batchClaim", false)
        val event = createOutboxEvent(status = OutboxStatus.PENDING)

        whenever(
            outboxEventRepository.findUnprocessedEvents(
                status = OutboxStatus.PENDING,
                retryStatus = OutboxStatus.FAILED,
                maxRetries = 3,
                limit = 100
            )
        ).thenReturn(listOf(event))
        whenever(
            outboxEventRepository.claimEventForPublish(
                id = eq(1L),
                nextStatus = eq(OutboxStatus.RETRYING),
                updatedAt = any(),
                pendingStatus = eq(OutboxStatus.PENDING),
                failedStatus = eq(OutboxStatus.FAILED),
                maxRetries = eq(3)
            )
        ).thenReturn(0)

        outboxEventPublisher.publishPendingEvents()

        verify(outboxEventRepository, never()).findById(any())
    }

    @Test
    fun 발행_실패_시_에러_메시지가_null이면_Unknown_error를_사용한다() {
        val event = createOutboxEvent(retryCount = 0)

        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event))
        whenever(avroEventConverter.convertJsonToAvro(event.eventType, event.eventData))
            .thenThrow(RuntimeException())
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer { it.arguments[0] as OutboxEvent }

        outboxEventPublisher.publishEvent(1L)

        assertThat(event.errorMessage).isEqualTo("Unknown error")
        assertThat(event.status).isEqualTo(OutboxStatus.FAILED)
        verify(outboxMetrics).recordOutboxFailed("payment")
    }

    @Test
    fun 대기중인_이벤트를_클레임_후_배치_발행한다() {
        ReflectionTestUtils.setField(outboxEventPublisher, "batchClaim", false)
        val event = createOutboxEvent(status = OutboxStatus.PENDING)
        val avroRecord = mock<GenericRecord>()
        val sendResult = createSendResult("payment", "1", avroRecord)

        whenever(
            outboxEventRepository.findUnprocessedEvents(
                status = OutboxStatus.PENDING,
                retryStatus = OutboxStatus.FAILED,
                maxRetries = 3,
                limit = 100
            )
        ).thenReturn(listOf(event))
        whenever(
            outboxEventRepository.claimEventForPublish(
                id = eq(1L),
                nextStatus = eq(OutboxStatus.RETRYING),
                updatedAt = any(),
                pendingStatus = eq(OutboxStatus.PENDING),
                failedStatus = eq(OutboxStatus.FAILED),
                maxRetries = eq(3)
            )
        ).thenReturn(1)

        val retryingEvent = createOutboxEvent(status = OutboxStatus.RETRYING)
        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(retryingEvent))
        whenever(avroEventConverter.convertJsonToAvro(retryingEvent.eventType, retryingEvent.eventData)).thenReturn(avroRecord)
        whenever(kafkaTemplate.send("payment", "1", avroRecord))
            .thenReturn(CompletableFuture.completedFuture(sendResult))
        mockKafkaTransaction(sendResult)
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer { it.arguments[0] as OutboxEvent }

        outboxEventPublisher.publishPendingEvents()

        verify(outboxEventRepository).claimEventForPublish(
            id = eq(1L),
            nextStatus = eq(OutboxStatus.RETRYING),
            updatedAt = any(),
            pendingStatus = eq(OutboxStatus.PENDING),
            failedStatus = eq(OutboxStatus.FAILED),
            maxRetries = eq(3)
        )
        assertThat(retryingEvent.status).isEqualTo(OutboxStatus.PUBLISHED)
    }

    @Test
    fun 여러_이벤트를_단일_Kafka_트랜잭션으로_배치_발행한다() {
        ReflectionTestUtils.setField(outboxEventPublisher, "batchClaim", false)
        val event1 = createOutboxEvent(id = 1L, status = OutboxStatus.PENDING)
        val event2 = createOutboxEvent(id = 2L, status = OutboxStatus.PENDING)
        val avroRecord = mock<GenericRecord>()
        val sendResult = createSendResult("payment", "1", avroRecord)

        whenever(
            outboxEventRepository.findUnprocessedEvents(
                status = OutboxStatus.PENDING,
                retryStatus = OutboxStatus.FAILED,
                maxRetries = 3,
                limit = 100
            )
        ).thenReturn(listOf(event1, event2))
        whenever(
            outboxEventRepository.claimEventForPublish(
                id = any(),
                nextStatus = eq(OutboxStatus.RETRYING),
                updatedAt = any(),
                pendingStatus = eq(OutboxStatus.PENDING),
                failedStatus = eq(OutboxStatus.FAILED),
                maxRetries = eq(3)
            )
        ).thenReturn(1)

        val retryingEvent1 = createOutboxEvent(id = 1L, status = OutboxStatus.RETRYING)
        val retryingEvent2 = createOutboxEvent(id = 2L, status = OutboxStatus.RETRYING)
        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(retryingEvent1))
        whenever(outboxEventRepository.findById(2L)).thenReturn(Optional.of(retryingEvent2))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(avroRecord)
        whenever(kafkaTemplate.send(any<String>(), any<String>(), any()))
            .thenReturn(CompletableFuture.completedFuture(sendResult))
        mockKafkaTransaction(sendResult)
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer { it.arguments[0] as OutboxEvent }

        outboxEventPublisher.publishPendingEvents()

        assertThat(retryingEvent1.status).isEqualTo(OutboxStatus.PUBLISHED)
        assertThat(retryingEvent2.status).isEqualTo(OutboxStatus.PUBLISHED)
        verify(outboxEventRepository).save(eq(retryingEvent1))
        verify(outboxEventRepository).save(eq(retryingEvent2))
        verify(outboxMetrics, Mockito.times(2)).recordOutboxPublished("payment")
    }

    @Test
    fun 행_단위_선점에서_메시지_쪽_실패면_재시도_횟수를_올려_FAILED로_저장한다() {
        ReflectionTestUtils.setField(outboxEventPublisher, "batchClaim", false)
        val event = createOutboxEvent(id = 1L, status = OutboxStatus.PENDING)
        val avroRecord = mock<GenericRecord>()

        whenever(
            outboxEventRepository.findUnprocessedEvents(
                status = OutboxStatus.PENDING,
                retryStatus = OutboxStatus.FAILED,
                maxRetries = 3,
                limit = 100
            )
        ).thenReturn(listOf(event))
        whenever(
            outboxEventRepository.claimEventForPublish(
                id = eq(1L),
                nextStatus = eq(OutboxStatus.RETRYING),
                updatedAt = any(),
                pendingStatus = eq(OutboxStatus.PENDING),
                failedStatus = eq(OutboxStatus.FAILED),
                maxRetries = eq(3)
            )
        ).thenReturn(1)

        val retryingEvent = createOutboxEvent(id = 1L, status = OutboxStatus.RETRYING)
        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(retryingEvent))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(avroRecord)
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        ).thenThrow(RecordTooLargeException("Kafka rejected the record"))
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer { it.arguments[0] as OutboxEvent }

        outboxEventPublisher.publishPendingEvents()

        assertThat(retryingEvent.status).isEqualTo(OutboxStatus.FAILED)
        assertThat(retryingEvent.retryCount).isEqualTo(1)
        verify(outboxMetrics).recordOutboxFailed("payment")
        verify(outboxMetrics, never()).recordBrokerFailure()
    }

    @Test
    fun 행_단위_선점에서_브로커_실패면_재시도_횟수를_올리지_않고_PENDING으로_저장한다() {
        val publisher = batchClaimPublisher(MutableClock(START))
        ReflectionTestUtils.setField(publisher, "batchClaim", false)
        val event = createOutboxEvent(id = 1L, status = OutboxStatus.PENDING)
        val retryingEvent = createOutboxEvent(id = 1L, status = OutboxStatus.RETRYING, retryCount = 2)
        whenever(
            outboxEventRepository.findUnprocessedEvents(
                status = OutboxStatus.PENDING,
                retryStatus = OutboxStatus.FAILED,
                maxRetries = 3,
                limit = 100
            )
        ).thenReturn(listOf(event))
        whenever(outboxEventRepository.claimEventForPublish(any(), any(), any(), any(), any(), any())).thenReturn(1)
        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(retryingEvent))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(mock<GenericRecord>())
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        ).thenThrow(KafkaException("Failed to construct kafka producer"))
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer { it.arguments[0] as OutboxEvent }

        publisher.publishPendingEvents()

        assertThat(retryingEvent.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(retryingEvent.retryCount).isEqualTo(2)
        assertThat(retryingEvent.processed).isFalse()
        verify(outboxEventRepository).save(eq(retryingEvent))
        verify(outboxMetrics, never()).recordOutboxFailed(any())
        verify(outboxMetrics).recordBrokerFailure()
        assertThat(publisher.isBrokerBackingOff()).isTrue()
    }

    @Test
    fun 일괄_선점_모드에서는_잠근_행을_한_번에_다시_읽어_생성_순서대로_발행한다() {
        val now = LocalDateTime.now()
        val later = createOutboxEvent(id = 1L, createdAt = now)
        val earlier = createOutboxEvent(id = 2L, createdAt = now.minusSeconds(1))
        val alreadyDone = createOutboxEvent(id = 3L, createdAt = now.minusSeconds(2))
        ReflectionTestUtils.setField(alreadyDone, "processed", true)
        val avroRecord = mock<GenericRecord>()
        val sendResult = createSendResult("payment", "1", avroRecord)

        whenever(outboxEventRepository.lockUnprocessedEventIds("PENDING", "FAILED", 3, 100))
            .thenReturn(listOf(1L, 2L, 3L))
        whenever(
            outboxEventRepository.claimEventsForPublish(
                ids = eq(listOf(1L, 2L, 3L)),
                nextStatus = eq("RETRYING"),
                updatedAt = any(),
                pendingStatus = eq("PENDING"),
                failedStatus = eq("FAILED"),
                maxRetries = eq(3)
            )
        ).thenReturn(3)
        whenever(outboxEventRepository.findAllById(listOf(1L, 2L, 3L))).thenReturn(listOf(later, earlier, alreadyDone))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(avroRecord)
        val sentIds = mutableListOf<Long?>()
        whenever(kafkaTemplate.send(eq("payment"), eq("1"), eq(avroRecord))).thenAnswer {
            CompletableFuture.completedFuture(sendResult)
        }
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        ).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as KafkaOperations.OperationsCallback<String, Any, Any>
            callback.doInOperations(kafkaTemplate)
        }
        whenever(outboxEventRepository.markClaimedEventsProcessed(any(), any(), any(), any(), any(), any())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            sentIds.addAll(it.arguments[0] as List<Long>)
            2
        }

        outboxEventPublisher.publishPendingEvents()

        assertThat(sentIds).containsExactly(2L, 1L)
        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
        verify(outboxEventRepository, never()).findUnprocessedEvents(any(), any(), any(), any())
        verify(outboxEventRepository, never()).findById(any())
    }

    @Test
    fun 일괄_선점_모드에서_잠근_행이_없으면_선점과_재조회를_하지_않는다() {
        whenever(outboxEventRepository.lockUnprocessedEventIds("PENDING", "FAILED", 3, 100)).thenReturn(emptyList())

        outboxEventPublisher.publishPendingEvents()

        verify(outboxEventRepository, never()).claimEventsForPublish(any(), any(), any(), any(), any(), any())
        verify(outboxEventRepository, never()).findAllById(any())
    }

    @Test
    fun 일괄_선점_갱신_수가_잠근_행_수와_달라도_잠근_행만_다시_읽는다() {
        val notRetrying = createOutboxEvent(id = 1L, status = OutboxStatus.PENDING)

        whenever(outboxEventRepository.lockUnprocessedEventIds("PENDING", "FAILED", 3, 100)).thenReturn(listOf(1L))
        whenever(outboxEventRepository.claimEventsForPublish(any(), any(), any(), any(), any(), any())).thenReturn(0)
        whenever(outboxEventRepository.findAllById(listOf(1L))).thenReturn(listOf(notRetrying))

        outboxEventPublisher.publishPendingEvents()

        verify(avroEventConverter, never()).convertJsonToAvro(any(), any())
        verify(kafkaTemplate, never()).executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
    }

    @Test
    fun 일괄_선점_뒤_한도를_넘기면_Kafka로_보내지_않고_행을_RETRYING으로_둔다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val event = createOutboxEvent(id = 1L)
        stubSingleClaim(event) { clock.advanceMillis(MAX_CLAIM_AGE_MS + 1) }
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(mock<GenericRecord>())
        runKafkaCallbackDirectly()

        publisher.publishPendingEvents()

        verify(outboxEventRepository).claimEventsForPublish(
            ids = eq(listOf(1L)),
            nextStatus = eq("RETRYING"),
            updatedAt = eq(LocalDateTime.ofInstant(START, clock.zone)),
            pendingStatus = eq("PENDING"),
            failedStatus = eq("FAILED"),
            maxRetries = eq(3)
        )
        verify(kafkaTemplate).executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        verify(kafkaTemplate, never()).send(any<String>(), any<String>(), any())
        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
        verify(outboxMetrics, never()).recordOutboxFailed(any())
        assertThat(event.status).isEqualTo(OutboxStatus.RETRYING)
        assertThat(event.retryCount).isZero()
        assertThat(event.processed).isFalse()
    }

    @Test
    fun 일괄_선점_뒤_전송_중에_한도를_넘기면_커밋하지_않고_행을_RETRYING으로_둔다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val event = createOutboxEvent(id = 1L)
        val avroRecord = mock<GenericRecord>()
        stubSingleClaim(event)
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(avroRecord)
        whenever(kafkaTemplate.send(eq("payment"), eq("1"), eq(avroRecord))).thenAnswer {
            clock.advanceMillis(MAX_CLAIM_AGE_MS + 1)
            CompletableFuture.completedFuture(createSendResult("payment", "1", avroRecord))
        }
        var aborted = false
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        ).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as KafkaOperations.OperationsCallback<String, Any, Any>
            try {
                callback.doInOperations(kafkaTemplate)
            } catch (e: RuntimeException) {
                aborted = true
                throw e
            }
        }

        publisher.publishPendingEvents()

        assertThat(aborted).isTrue()
        verify(kafkaTemplate).send(eq("payment"), eq("1"), eq(avroRecord))
        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
        verify(outboxMetrics, never()).recordOutboxPublished(any())
        verify(outboxMetrics, never()).recordOutboxFailed(any())
        assertThat(event.status).isEqualTo(OutboxStatus.RETRYING)
        assertThat(event.retryCount).isZero()
    }

    @Test
    fun 일괄_선점_뒤_경과가_한도와_같으면_기존처럼_발행한다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val event = createOutboxEvent(id = 1L)
        val avroRecord = mock<GenericRecord>()
        stubSingleClaim(event) { clock.advanceMillis(MAX_CLAIM_AGE_MS) }
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(avroRecord)
        whenever(kafkaTemplate.send(eq("payment"), eq("1"), eq(avroRecord)))
            .thenReturn(CompletableFuture.completedFuture(createSendResult("payment", "1", avroRecord)))
        runKafkaCallbackDirectly()
        whenever(outboxEventRepository.markClaimedEventsProcessed(any(), any(), any(), any(), any(), any())).thenReturn(1)

        publisher.publishPendingEvents()

        verify(outboxEventRepository).markClaimedEventsProcessed(
            ids = eq(listOf(1L)),
            publishedStatus = eq("PUBLISHED"),
            processedAt = any(),
            updatedAt = any(),
            claimedStatus = eq("RETRYING"),
            claimedAt = eq(LocalDateTime.ofInstant(START, clock.zone))
        )
        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
        verify(outboxMetrics).recordOutboxPublished("payment")
        verify(outboxMetrics).recordPublishLatency(event.createdAt)
    }

    @Test
    fun 한도_안이면_꽉_찬_배치_뒤에_다음_배치를_이어서_잠근다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val ids = (1L..100L).toList()
        val event = createOutboxEvent(id = 1L)
        val avroRecord = mock<GenericRecord>()
        whenever(outboxEventRepository.lockUnprocessedEventIds("PENDING", "FAILED", 3, 100)).thenReturn(ids, emptyList())
        whenever(outboxEventRepository.claimEventsForPublish(eq(ids), any(), any(), any(), any(), any())).thenReturn(100)
        whenever(outboxEventRepository.findAllById(ids)).thenReturn(listOf(event))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(avroRecord)
        whenever(kafkaTemplate.send(eq("payment"), eq("1"), eq(avroRecord)))
            .thenReturn(CompletableFuture.completedFuture(createSendResult("payment", "1", avroRecord)))
        runKafkaCallbackDirectly()
        whenever(outboxEventRepository.markClaimedEventsProcessed(any(), any(), any(), any(), any(), any())).thenReturn(1)

        publisher.publishPendingEvents()

        verify(outboxEventRepository, times(2)).lockUnprocessedEventIds(any(), any(), any(), any())
    }

    @Test
    fun 한도를_넘겨_버린_배치_뒤에는_꽉_찬_배치여도_이어_돌지_않는다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val ids = (1L..100L).toList()
        val event = createOutboxEvent(id = 1L)
        whenever(outboxEventRepository.lockUnprocessedEventIds("PENDING", "FAILED", 3, 100)).thenReturn(ids, emptyList())
        whenever(outboxEventRepository.claimEventsForPublish(eq(ids), any(), any(), any(), any(), any())).thenReturn(100)
        whenever(outboxEventRepository.findAllById(ids)).thenAnswer {
            clock.advanceMillis(MAX_CLAIM_AGE_MS + 1)
            listOf(event)
        }
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(mock<GenericRecord>())
        runKafkaCallbackDirectly()

        publisher.publishPendingEvents()

        verify(outboxEventRepository, times(1)).lockUnprocessedEventIds(any(), any(), any(), any())
        verify(kafkaTemplate, never()).send(any<String>(), any<String>(), any())
        assertThat(event.status).isEqualTo(OutboxStatus.RETRYING)
    }

    @Test
    fun 선점이_한도를_넘긴_뒤_Kafka_트랜잭션이_실패하면_행을_FAILED로_바꾸지_않고_백오프만_건다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val event = createOutboxEvent(id = 1L)
        stubSingleClaim(event)
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(mock<GenericRecord>())
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        ).thenAnswer {
            clock.advanceMillis(MAX_CLAIM_AGE_MS + 1)
            throw RuntimeException("producer fenced")
        }

        publisher.publishPendingEvents()

        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
        verify(outboxEventRepository, never()).releaseClaimedEvents(any(), any(), any(), any(), any())
        verify(outboxMetrics, never()).recordOutboxFailed(any())
        assertThat(event.status).isEqualTo(OutboxStatus.RETRYING)
        assertThat(event.retryCount).isZero()
        assertThat(publisher.isBrokerBackingOff()).isTrue()
    }

    @Test
    fun 선점이_한도_안일_때_메시지_쪽_실패면_기존처럼_FAILED로_바꾼다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val event = createOutboxEvent(id = 1L)
        stubSingleClaim(event)
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(mock<GenericRecord>())
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        ).thenThrow(RecordTooLargeException("record too large"))
        whenever(outboxEventRepository.markClaimedEventFailed(any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(1)

        publisher.publishPendingEvents()

        assertThat(event.status).isEqualTo(OutboxStatus.FAILED)
        assertThat(event.retryCount).isEqualTo(1)
        assertThat(publisher.isBrokerBackingOff()).isFalse()
        verify(outboxEventRepository).markClaimedEventFailed(
            id = eq(1L),
            status = eq(OutboxStatus.FAILED),
            errorMessage = eq("record too large"),
            retryCount = eq(1),
            processed = eq(false),
            processedAt = eq(null),
            updatedAt = eq(event.updatedAt),
            claimedStatus = eq(OutboxStatus.RETRYING),
            claimedAt = eq(LocalDateTime.ofInstant(START, clock.zone))
        )
        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
        verify(outboxMetrics).recordOutboxFailed("payment")
    }

    @Test
    fun 기동_시_한도와_트랜잭션_제한_시간의_합이_stale_기준_이상이면_경고만_남기고_발행은_계속한다() {
        ReflectionTestUtils.setField(outboxEventPublisher, "staleThresholdMs", 60_000L)
        whenever(outboxEventRepository.lockUnprocessedEventIds("PENDING", "FAILED", 3, 100)).thenReturn(emptyList())

        val warnings = captureWarnings { outboxEventPublisher.warnIfClaimAgeExceedsStaleWindow() }
        outboxEventPublisher.publishPendingEvents()

        assertThat(warnings).hasSize(1)
        assertThat(warnings[0]).contains(
            "max-claim-age-ms(300000)",
            "transaction.timeout.ms(60000)",
            "broker-tx-cleanup-ms(10000)",
            "stale-threshold-ms(60000)"
        )
        verify(outboxEventRepository).lockUnprocessedEventIds("PENDING", "FAILED", 3, 100)
    }

    @Test
    fun 기동_시_한도와_트랜잭션_제한_시간과_브로커_정리_주기의_합이_stale_기준과_같으면_경고한다() {
        ReflectionTestUtils.setField(outboxEventPublisher, "staleThresholdMs", 370_000L)

        val warnings = captureWarnings { outboxEventPublisher.warnIfClaimAgeExceedsStaleWindow() }

        assertThat(warnings).hasSize(1)
        assertThat(warnings[0]).contains(
            "max-claim-age-ms(300000)",
            "transaction.timeout.ms(60000)",
            "broker-tx-cleanup-ms(10000)",
            "stale-threshold-ms(370000)"
        )
    }

    @Test
    fun 기동_시_세_값의_합이_stale_기준보다_1_작으면_경고하지_않는다() {
        ReflectionTestUtils.setField(outboxEventPublisher, "staleThresholdMs", 370_001L)

        val warnings = captureWarnings { outboxEventPublisher.warnIfClaimAgeExceedsStaleWindow() }

        assertThat(warnings).isEmpty()
    }

    @Test
    fun 기동_시_브로커_정리_주기_설정을_바꾸면_그_값을_더해_부등식을_검사한다() {
        ReflectionTestUtils.setField(outboxEventPublisher, "brokerTxCleanupMs", 240_000L)
        val warned = captureWarnings { outboxEventPublisher.warnIfClaimAgeExceedsStaleWindow() }

        ReflectionTestUtils.setField(outboxEventPublisher, "brokerTxCleanupMs", 239_999L)
        val passed = captureWarnings { outboxEventPublisher.warnIfClaimAgeExceedsStaleWindow() }

        assertThat(warned).hasSize(1)
        assertThat(warned[0]).contains("broker-tx-cleanup-ms(240000)", "stale-threshold-ms(600000)")
        assertThat(passed).isEmpty()
    }

    @Test
    fun 기동_시_프로듀서_설정의_transaction_timeout_ms를_읽어_부등식을_검사한다() {
        val producerFactory = mock<ProducerFactory<String, Any>>()
        whenever(producerFactory.configurationProperties)
            .thenReturn(mapOf<String, Any>(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG to "300000"))
        whenever(kafkaTemplate.producerFactory).thenReturn(producerFactory)
        whenever(outboxEventRepository.lockUnprocessedEventIds("PENDING", "FAILED", 3, 100)).thenReturn(emptyList())

        val warnings = captureWarnings { outboxEventPublisher.warnIfClaimAgeExceedsStaleWindow() }
        outboxEventPublisher.publishPendingEvents()

        assertThat(warnings).hasSize(1)
        assertThat(warnings[0]).contains("transaction.timeout.ms(300000)", "stale-threshold-ms(600000)")
    }

    @Test
    fun 기동_시_기본값이면_부등식이_서서_경고를_남기지_않는다() {
        whenever(outboxEventRepository.lockUnprocessedEventIds("PENDING", "FAILED", 3, 100)).thenReturn(emptyList())

        val warnings = captureWarnings { outboxEventPublisher.warnIfClaimAgeExceedsStaleWindow() }
        outboxEventPublisher.publishPendingEvents()

        assertThat(warnings).isEmpty()
    }

    @Test
    fun 일괄_선점이_꺼져_있으면_기동_시_부등식을_검사하지_않는다() {
        ReflectionTestUtils.setField(outboxEventPublisher, "batchClaim", false)
        ReflectionTestUtils.setField(outboxEventPublisher, "staleThresholdMs", 1_000L)

        val warnings = captureWarnings { outboxEventPublisher.warnIfClaimAgeExceedsStaleWindow() }
        outboxEventPublisher.publishPendingEvents()

        assertThat(warnings).isEmpty()
        verify(kafkaTemplate, never()).producerFactory
    }

    @Test
    fun 기동_시_연결된_DB가_MySQL이면_일괄_선점을_그대로_쓴다() {
        val publisher = publisherWith(dataSource(productName = "MySQL"))
        whenever(outboxEventRepository.lockUnprocessedEventIds("PENDING", "FAILED", 3, 100)).thenReturn(emptyList())

        val logs = captureLogs(Level.INFO) { publisher.configureClaimMode() }
        publisher.publishPendingEvents()

        assertThat(ReflectionTestUtils.getField(publisher, "batchClaim")).isEqualTo(true)
        assertThat(logs).noneMatch { it.contains("batch claim disabled") }
        verify(outboxEventRepository, never()).findUnprocessedEvents(any(), any(), any(), any())
    }

    @Test
    fun 기동_시_연결된_DB가_MySQL이_아니면_행_단위_선점으로_돌고_한_줄_남긴다() {
        val publisher = publisherWith(dataSource(productName = "H2"))
        ReflectionTestUtils.setField(publisher, "staleThresholdMs", 1_000L)
        whenever(
            outboxEventRepository.findUnprocessedEvents(
                status = OutboxStatus.PENDING,
                retryStatus = OutboxStatus.FAILED,
                maxRetries = 3,
                limit = 100
            )
        ).thenReturn(emptyList())

        val logs = captureLogs(Level.INFO) { publisher.configureClaimMode() }
        publisher.publishPendingEvents()

        assertThat(ReflectionTestUtils.getField(publisher, "batchClaim")).isEqualTo(false)
        assertThat(publisher.staleClaimTimestamp()).isNull()
        assertThat(logs).containsExactly(
            "Outbox batch claim disabled: connected database is H2, not MySQL; publishing with per-row claims"
        )
        verify(outboxEventRepository, never()).lockUnprocessedEventIds(any(), any(), any(), any())
        verify(kafkaTemplate, never()).producerFactory
    }

    @Test
    fun 기동_시_DB_판별에_실패하면_행_단위_선점으로_돈다() {
        val dataSource = mock<DataSource>()
        whenever(dataSource.connection).thenThrow(SQLException("connection refused"))
        val publisher = publisherWith(dataSource)

        val logs = captureLogs(Level.INFO) { publisher.configureClaimMode() }

        assertThat(ReflectionTestUtils.getField(publisher, "batchClaim")).isEqualTo(false)
        assertThat(logs).containsExactly(
            "Outbox could not read the connected database product name: connection refused",
            "Outbox batch claim disabled: connected database is unknown, not MySQL; publishing with per-row claims"
        )
    }

    @Test
    fun 일괄_선점을_설정으로_끄면_기동_시_DB를_판별하지_않는다() {
        val dataSource = mock<DataSource>()
        val publisher = publisherWith(dataSource)
        ReflectionTestUtils.setField(publisher, "batchClaim", false)

        val logs = captureLogs(Level.INFO) { publisher.configureClaimMode() }

        assertThat(logs).isEmpty()
        verify(dataSource, never()).connection
    }

    @Test
    fun 일괄_선점_시각은_마이크로초로_잘라_선점과_완료_조건에_같은_값을_쓴다() {
        val clock = MutableClock(START.plusNanos(123_456_789))
        val publisher = batchClaimPublisher(clock)
        val event = createOutboxEvent(id = 1L)
        val avroRecord = mock<GenericRecord>()
        val claimedAt = LocalDateTime.ofInstant(START.plusNanos(123_456_000), clock.zone)
        stubSingleClaim(event)
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(avroRecord)
        whenever(kafkaTemplate.send(eq("payment"), eq("1"), eq(avroRecord)))
            .thenReturn(CompletableFuture.completedFuture(createSendResult("payment", "1", avroRecord)))
        runKafkaCallbackDirectly()
        whenever(outboxEventRepository.markClaimedEventsProcessed(any(), any(), any(), any(), any(), any())).thenReturn(1)

        publisher.publishPendingEvents()

        verify(outboxEventRepository).claimEventsForPublish(
            ids = eq(listOf(1L)),
            nextStatus = eq("RETRYING"),
            updatedAt = eq(claimedAt),
            pendingStatus = eq("PENDING"),
            failedStatus = eq("FAILED"),
            maxRetries = eq(3)
        )
        verify(outboxEventRepository).markClaimedEventsProcessed(
            ids = eq(listOf(1L)),
            publishedStatus = eq("PUBLISHED"),
            processedAt = any(),
            updatedAt = any(),
            claimedStatus = eq("RETRYING"),
            claimedAt = eq(claimedAt)
        )
    }

    @Test
    fun 일괄_선점_완료_표시가_보낸_행보다_적게_바뀌면_경고를_남기고_지표는_행마다_기록한다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val first = createOutboxEvent(id = 1L)
        val second = createOutboxEvent(id = 2L)
        val avroRecord = mock<GenericRecord>()
        whenever(outboxEventRepository.lockUnprocessedEventIds("PENDING", "FAILED", 3, 100)).thenReturn(listOf(1L, 2L))
        whenever(outboxEventRepository.claimEventsForPublish(any(), any(), any(), any(), any(), any())).thenReturn(2)
        whenever(outboxEventRepository.findAllById(listOf(1L, 2L))).thenReturn(listOf(first, second))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(avroRecord)
        whenever(kafkaTemplate.send(eq("payment"), eq("1"), eq(avroRecord)))
            .thenReturn(CompletableFuture.completedFuture(createSendResult("payment", "1", avroRecord)))
        runKafkaCallbackDirectly()
        whenever(outboxEventRepository.markClaimedEventsProcessed(any(), any(), any(), any(), any(), any())).thenReturn(1)

        val warnings = captureWarnings { publisher.publishPendingEvents() }

        assertThat(warnings).anyMatch { it.contains("marked 1 of 2 published events") }
        verify(outboxMetrics, times(2)).recordOutboxPublished("payment")
        verify(outboxMetrics, times(2)).recordPublishLatency(any())
    }

    @Test
    fun 일괄_선점에서_변환에_실패한_행은_선점_시각_조건으로_실패를_표시한다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val event = createOutboxEvent(id = 1L, retryCount = 2)
        stubSingleClaim(event)
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenThrow(RuntimeException("bad json"))
        whenever(outboxEventRepository.markClaimedEventFailed(any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(0)

        val warnings = captureWarnings { publisher.publishPendingEvents() }

        verify(outboxEventRepository).markClaimedEventFailed(
            id = eq(1L),
            status = eq(OutboxStatus.FAILED),
            errorMessage = eq("Max retries exceeded: bad json"),
            retryCount = eq(3),
            processed = eq(true),
            processedAt = eq(event.processedAt),
            updatedAt = eq(event.updatedAt),
            claimedStatus = eq(OutboxStatus.RETRYING),
            claimedAt = eq(LocalDateTime.ofInstant(START, clock.zone))
        )
        assertThat(event.processedAt).isNotNull()
        assertThat(warnings).anyMatch { it.contains("skipped failure mark") }
        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
        verify(kafkaTemplate, never()).executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
    }

    @Test
    fun 일괄_선점이_꺼져_있으면_복구용_선점_시각을_주지_않는다() {
        ReflectionTestUtils.setField(outboxEventPublisher, "batchClaim", false)
        assertThat(outboxEventPublisher.staleClaimTimestamp()).isNull()
    }

    @Test
    fun 일괄_선점이_켜져_있으면_복구용_선점_시각을_시계에서_마이크로초로_잘라_준다() {
        val clock = MutableClock(START.plusNanos(987_654_321))
        val publisher = batchClaimPublisher(clock)

        assertThat(publisher.staleClaimTimestamp())
            .isEqualTo(LocalDateTime.ofInstant(START.plusNanos(987_654_000), clock.zone))
    }

    @Test
    fun 복구_발행은_선점_뒤_한도_안이면_보내고_선점_시각_조건으로_완료를_표시한다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val claimedAt = LocalDateTime.ofInstant(START, clock.zone)
        val event = createOutboxEvent(id = 1L)
        val avroRecord = mock<GenericRecord>()
        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(avroRecord)
        whenever(kafkaTemplate.send(eq("payment"), eq("1"), eq(avroRecord))).thenAnswer {
            clock.advanceMillis(MAX_CLAIM_AGE_MS)
            CompletableFuture.completedFuture(createSendResult("payment", "1", avroRecord))
        }
        runKafkaCallbackDirectly()
        whenever(outboxEventRepository.markClaimedEventsProcessed(any(), any(), any(), any(), any(), any())).thenReturn(1)

        publisher.publishEvent(1L, claimedAt)

        verify(outboxEventRepository).markClaimedEventsProcessed(
            ids = eq(listOf(1L)),
            publishedStatus = eq("PUBLISHED"),
            processedAt = any(),
            updatedAt = any(),
            claimedStatus = eq("RETRYING"),
            claimedAt = eq(claimedAt)
        )
        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
        verify(outboxMetrics).recordOutboxPublished("payment")
    }

    @Test
    fun 복구_발행은_선점_뒤_한도를_넘기면_보내지_않고_행을_RETRYING으로_둔다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val claimedAt = LocalDateTime.ofInstant(START, clock.zone)
        val event = createOutboxEvent(id = 1L)
        whenever(outboxEventRepository.findById(1L)).thenAnswer {
            clock.advanceMillis(MAX_CLAIM_AGE_MS + 1)
            Optional.of(event)
        }
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(mock<GenericRecord>())
        runKafkaCallbackDirectly()

        val warnings = captureWarnings { publisher.publishEvent(1L, claimedAt) }

        assertThat(warnings).anyMatch { it.contains("stale claim expired after") }
        verify(kafkaTemplate, never()).send(any<String>(), any<String>(), any())
        verify(outboxEventRepository, never()).markClaimedEventsProcessed(any(), any(), any(), any(), any(), any())
        verify(outboxEventRepository, never())
            .markClaimedEventFailed(any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any())
        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
        assertThat(event.status).isEqualTo(OutboxStatus.RETRYING)
    }

    @Test
    fun 복구_발행은_전송_뒤_커밋_전에_한도를_넘기면_커밋하지_않는다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val event = createOutboxEvent(id = 1L)
        val avroRecord = mock<GenericRecord>()
        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(avroRecord)
        whenever(kafkaTemplate.send(eq("payment"), eq("1"), eq(avroRecord))).thenAnswer {
            clock.advanceMillis(MAX_CLAIM_AGE_MS + 1)
            CompletableFuture.completedFuture(createSendResult("payment", "1", avroRecord))
        }
        var aborted = false
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        ).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as KafkaOperations.OperationsCallback<String, Any, Any>
            try {
                callback.doInOperations(kafkaTemplate)
            } catch (e: RuntimeException) {
                aborted = true
                throw e
            }
        }

        publisher.publishEvent(1L, LocalDateTime.ofInstant(START, clock.zone))

        assertThat(aborted).isTrue()
        verify(outboxEventRepository, never()).markClaimedEventsProcessed(any(), any(), any(), any(), any(), any())
        verify(outboxMetrics, never()).recordOutboxFailed(any())
        assertThat(event.status).isEqualTo(OutboxStatus.RETRYING)
    }

    @Test
    fun 복구_발행이_한도를_넘긴_뒤_Kafka가_실패하면_실패를_표시하지_않는다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val event = createOutboxEvent(id = 1L)
        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(mock<GenericRecord>())
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        ).thenAnswer {
            clock.advanceMillis(MAX_CLAIM_AGE_MS + 1)
            throw RuntimeException("producer fenced")
        }

        publisher.publishEvent(1L, LocalDateTime.ofInstant(START, clock.zone))

        verify(outboxEventRepository, never())
            .markClaimedEventFailed(any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any())
        verify(outboxMetrics, never()).recordOutboxFailed(any())
        assertThat(event.status).isEqualTo(OutboxStatus.RETRYING)
    }

    @Test
    fun 복구_발행이_한도_안에서_메시지_쪽_실패면_선점_시각_조건으로_실패를_표시한다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val claimedAt = LocalDateTime.ofInstant(START, clock.zone)
        val event = createOutboxEvent(id = 1L)
        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(mock<GenericRecord>())
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        ).thenThrow(RecordTooLargeException("record too large"))
        whenever(outboxEventRepository.markClaimedEventFailed(any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(1)

        publisher.publishEvent(1L, claimedAt)

        verify(outboxEventRepository).markClaimedEventFailed(
            id = eq(1L),
            status = eq(OutboxStatus.FAILED),
            errorMessage = eq("record too large"),
            retryCount = eq(1),
            processed = eq(false),
            processedAt = eq(null),
            updatedAt = eq(event.updatedAt),
            claimedStatus = eq(OutboxStatus.RETRYING),
            claimedAt = eq(claimedAt)
        )
        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
    }

    @Test
    fun 선점_시각_없이_부른_복구_발행은_시계를_보지_않고_기존처럼_save한다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val event = createOutboxEvent(id = 1L)
        val avroRecord = mock<GenericRecord>()
        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(avroRecord)
        whenever(kafkaTemplate.send(eq("payment"), eq("1"), eq(avroRecord))).thenAnswer {
            clock.advanceMillis(MAX_CLAIM_AGE_MS * 10)
            CompletableFuture.completedFuture(createSendResult("payment", "1", avroRecord))
        }
        runKafkaCallbackDirectly()
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer { it.arguments[0] as OutboxEvent }

        publisher.publishEvent(1L)

        assertThat(event.status).isEqualTo(OutboxStatus.PUBLISHED)
        verify(outboxEventRepository).save(eq(event))
        verify(outboxEventRepository, never()).markClaimedEventsProcessed(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun 브로커_실패는_재시도_횟수를_올리지_않고_선점_시각_조건으로_PENDING으로_되돌린다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val event = createOutboxEvent(id = 1L, retryCount = 2)
        stubSingleClaim(event)
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(mock<GenericRecord>())
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        ).thenThrow(KafkaException("Failed to construct kafka producer"))
        whenever(outboxEventRepository.releaseClaimedEvents(any(), any(), any(), any(), any())).thenReturn(1)

        publisher.publishPendingEvents()

        verify(outboxEventRepository).releaseClaimedEvents(
            ids = eq(listOf(1L)),
            pendingStatus = eq(OutboxStatus.PENDING),
            updatedAt = any(),
            claimedStatus = eq(OutboxStatus.RETRYING),
            claimedAt = eq(LocalDateTime.ofInstant(START, clock.zone))
        )
        verify(outboxEventRepository, never())
            .markClaimedEventFailed(any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any())
        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
        verify(outboxMetrics, never()).recordOutboxFailed(any())
        verify(outboxMetrics).recordBrokerFailure()
        assertThat(event.retryCount).isEqualTo(2)
        assertThat(event.processed).isFalse()
        assertThat(publisher.isBrokerBackingOff()).isTrue()
    }

    @Test
    fun 브로커_실패로_되돌릴_때_다른_쪽이_다시_선점한_행은_건너뛰고_경고를_남긴다() {
        val publisher = batchClaimPublisher(MutableClock(START))
        stubSingleClaim(createOutboxEvent(id = 1L))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(mock<GenericRecord>())
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        ).thenThrow(TimeoutException("Timeout expired after 60000ms while awaiting InitProducerId"))
        whenever(outboxEventRepository.releaseClaimedEvents(any(), any(), any(), any(), any())).thenReturn(0)

        val warnings = captureWarnings { publisher.publishPendingEvents() }

        assertThat(warnings).anyMatch { it.contains("released 0 of 1 claimed events") }
        assertThat(warnings).anyMatch { it.contains("paused for 1000ms after a broker-side failure") }
    }

    @Test
    fun 배치에_나쁜_행이_하나_있으면_그_행만_재시도_횟수가_오르고_나머지는_발행된다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val claimedAt = LocalDateTime.ofInstant(START, clock.zone)
        val now = LocalDateTime.now()
        val first = createOutboxEvent(id = 1L, createdAt = now.minusSeconds(3), partitionKey = "k1")
        val bad = createOutboxEvent(id = 2L, createdAt = now.minusSeconds(2), partitionKey = "bad")
        val third = createOutboxEvent(id = 3L, createdAt = now.minusSeconds(1), partitionKey = "k3")
        val avroRecord = mock<GenericRecord>()
        whenever(outboxEventRepository.lockUnprocessedEventIds("PENDING", "FAILED", 3, 100)).thenReturn(listOf(1L, 2L, 3L))
        whenever(outboxEventRepository.claimEventsForPublish(any(), any(), any(), any(), any(), any())).thenReturn(3)
        whenever(outboxEventRepository.findAllById(listOf(1L, 2L, 3L))).thenReturn(listOf(first, bad, third))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(avroRecord)
        whenever(kafkaTemplate.send(eq("payment"), any<String>(), eq(avroRecord))).thenAnswer { invocation ->
            val key = invocation.arguments[1] as String
            if (key == "bad") {
                CompletableFuture.failedFuture<SendResult<String, Any>>(RecordTooLargeException("record too large"))
            } else {
                CompletableFuture.completedFuture(createSendResult("payment", key, avroRecord))
            }
        }
        runKafkaCallbackDirectly()
        val published = mutableListOf<Long>()
        whenever(outboxEventRepository.markClaimedEventsProcessed(any(), any(), any(), any(), any(), any())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val ids = it.arguments[0] as List<Long>
            published.addAll(ids)
            ids.size
        }
        val failedIds = mutableListOf<Long>()
        whenever(outboxEventRepository.markClaimedEventFailed(any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenAnswer {
                failedIds.add(it.arguments[0] as Long)
                1
            }

        publisher.publishPendingEvents()

        assertThat(published).containsExactly(1L, 3L)
        assertThat(failedIds).containsExactly(2L)
        verify(outboxEventRepository).markClaimedEventFailed(
            id = eq(2L),
            status = eq(OutboxStatus.FAILED),
            errorMessage = anyOrNull(),
            retryCount = eq(1),
            processed = eq(false),
            processedAt = anyOrNull(),
            updatedAt = any(),
            claimedStatus = eq(OutboxStatus.RETRYING),
            claimedAt = eq(claimedAt)
        )
        assertThat(bad.retryCount).isEqualTo(1)
        assertThat(bad.errorMessage).contains("record too large")
        assertThat(first.retryCount).isZero()
        assertThat(third.retryCount).isZero()
        verify(outboxMetrics, times(1)).recordOutboxFailed("payment")
        verify(outboxMetrics, times(2)).recordOutboxPublished("payment")
        verify(outboxMetrics, never()).recordBrokerFailure()
        verify(outboxEventRepository, never()).releaseClaimedEvents(any(), any(), any(), any(), any())
        assertThat(publisher.isBrokerBackingOff()).isFalse()
    }

    @Test
    fun 하나씩_보내다가_브로커_실패가_나면_남은_행은_PENDING으로_되돌리고_멈춘다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val claimedAt = LocalDateTime.ofInstant(START, clock.zone)
        val now = LocalDateTime.now()
        val bad = createOutboxEvent(id = 1L, createdAt = now.minusSeconds(3), partitionKey = "bad")
        val second = createOutboxEvent(id = 2L, createdAt = now.minusSeconds(2), partitionKey = "k2")
        val third = createOutboxEvent(id = 3L, createdAt = now.minusSeconds(1), partitionKey = "k3")
        val avroRecord = mock<GenericRecord>()
        whenever(outboxEventRepository.lockUnprocessedEventIds("PENDING", "FAILED", 3, 100)).thenReturn(listOf(1L, 2L, 3L))
        whenever(outboxEventRepository.claimEventsForPublish(any(), any(), any(), any(), any(), any())).thenReturn(3)
        whenever(outboxEventRepository.findAllById(listOf(1L, 2L, 3L))).thenReturn(listOf(bad, second, third))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(avroRecord)
        val secondSends = AtomicInteger()
        whenever(kafkaTemplate.send(eq("payment"), any<String>(), eq(avroRecord))).thenAnswer { invocation ->
            val key = invocation.arguments[1] as String
            when {
                key == "bad" ->
                    CompletableFuture.failedFuture<SendResult<String, Any>>(RecordTooLargeException("record too large"))
                key == "k2" && secondSends.incrementAndGet() > 1 ->
                    CompletableFuture.failedFuture<SendResult<String, Any>>(TimeoutException("Expiring 1 record(s)"))
                else -> CompletableFuture.completedFuture(createSendResult("payment", key, avroRecord))
            }
        }
        runKafkaCallbackDirectly()
        whenever(outboxEventRepository.markClaimedEventFailed(any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(1)
        whenever(outboxEventRepository.releaseClaimedEvents(any(), any(), any(), any(), any())).thenReturn(1)

        publisher.publishPendingEvents()

        verify(outboxEventRepository).markClaimedEventFailed(
            id = eq(1L),
            status = eq(OutboxStatus.FAILED),
            errorMessage = anyOrNull(),
            retryCount = eq(1),
            processed = eq(false),
            processedAt = anyOrNull(),
            updatedAt = any(),
            claimedStatus = eq(OutboxStatus.RETRYING),
            claimedAt = eq(claimedAt)
        )
        verify(outboxEventRepository).releaseClaimedEvents(
            ids = eq(listOf(2L)),
            pendingStatus = eq(OutboxStatus.PENDING),
            updatedAt = any(),
            claimedStatus = eq(OutboxStatus.RETRYING),
            claimedAt = eq(claimedAt)
        )
        verify(outboxEventRepository).releaseClaimedEvents(
            ids = eq(listOf(3L)),
            pendingStatus = eq(OutboxStatus.PENDING),
            updatedAt = any(),
            claimedStatus = eq(OutboxStatus.RETRYING),
            claimedAt = eq(claimedAt)
        )
        verify(kafkaTemplate, times(1)).send(eq("payment"), eq("k3"), eq(avroRecord))
        verify(outboxEventRepository, never()).markClaimedEventsProcessed(any(), any(), any(), any(), any(), any())
        assertThat(second.retryCount).isZero()
        assertThat(third.retryCount).isZero()
        verify(outboxMetrics).recordBrokerFailure()
        assertThat(publisher.isBrokerBackingOff()).isTrue()
    }

    @Test
    fun 하나씩_보내다가_선점_한도를_넘기면_남은_행은_RETRYING으로_두고_멈춘다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val now = LocalDateTime.now()
        val bad = createOutboxEvent(id = 1L, createdAt = now.minusSeconds(2), partitionKey = "bad")
        val second = createOutboxEvent(id = 2L, createdAt = now.minusSeconds(1), partitionKey = "k2")
        val avroRecord = mock<GenericRecord>()
        whenever(outboxEventRepository.lockUnprocessedEventIds("PENDING", "FAILED", 3, 100)).thenReturn(listOf(1L, 2L))
        whenever(outboxEventRepository.claimEventsForPublish(any(), any(), any(), any(), any(), any())).thenReturn(2)
        whenever(outboxEventRepository.findAllById(listOf(1L, 2L))).thenReturn(listOf(bad, second))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(avroRecord)
        whenever(kafkaTemplate.send(eq("payment"), any<String>(), eq(avroRecord))).thenAnswer { invocation ->
            val key = invocation.arguments[1] as String
            if (key == "bad") {
                CompletableFuture.failedFuture<SendResult<String, Any>>(RecordTooLargeException("record too large"))
            } else {
                CompletableFuture.completedFuture(createSendResult("payment", key, avroRecord))
            }
        }
        runKafkaCallbackDirectly()
        whenever(outboxEventRepository.markClaimedEventFailed(any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenAnswer {
                clock.advanceMillis(MAX_CLAIM_AGE_MS + 1)
                1
            }

        publisher.publishPendingEvents()

        verify(kafkaTemplate, times(1)).send(eq("payment"), eq("k2"), eq(avroRecord))
        verify(outboxEventRepository, never()).markClaimedEventsProcessed(any(), any(), any(), any(), any(), any())
        verify(outboxEventRepository, never()).releaseClaimedEvents(any(), any(), any(), any(), any())
        assertThat(second.status).isEqualTo(OutboxStatus.RETRYING)
        assertThat(second.retryCount).isZero()
    }

    @Test
    fun 백오프_중에는_예약_실행과_깨우기_모두_발행하지_않는다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        stubSingleClaim(createOutboxEvent(id = 1L))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(mock<GenericRecord>())
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        ).thenThrow(KafkaException("Failed to construct kafka producer"))
        whenever(outboxEventRepository.releaseClaimedEvents(any(), any(), any(), any(), any())).thenReturn(1)
        val waker = OutboxPublishWaker(publisher, true)

        try {
            publisher.publishPendingEvents()
            publisher.publishPendingEvents()
            waker.wake()

            verify(outboxEventRepository, Mockito.after(300).times(1)).lockUnprocessedEventIds(any(), any(), any(), any())
            assertThat(publisher.isBrokerBackingOff()).isTrue()

            clock.advanceMillis(1_000)
            waker.wake()

            verify(outboxEventRepository, timeout(1_000).times(2)).lockUnprocessedEventIds(any(), any(), any(), any())
        } finally {
            waker.shutdown()
        }
    }

    @Test
    fun 브로커가_돌아와_한_번_발행에_성공하면_백오프_단계가_처음으로_돌아간다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val avroRecord = mock<GenericRecord>()
        stubSingleClaim(createOutboxEvent(id = 1L))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(avroRecord)
        whenever(kafkaTemplate.send(eq("payment"), eq("1"), eq(avroRecord)))
            .thenReturn(CompletableFuture.completedFuture(createSendResult("payment", "1", avroRecord)))
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        ).thenThrow(TimeoutException("broker down"))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val callback = invocation.arguments[0] as KafkaOperations.OperationsCallback<String, Any, Any>
                callback.doInOperations(kafkaTemplate)
            }
            .thenThrow(TimeoutException("broker down"))
        whenever(outboxEventRepository.releaseClaimedEvents(any(), any(), any(), any(), any())).thenReturn(1)
        whenever(outboxEventRepository.markClaimedEventsProcessed(any(), any(), any(), any(), any(), any())).thenReturn(1)

        publisher.publishPendingEvents()
        clock.advanceMillis(1_000)
        publisher.publishPendingEvents()
        assertThat(publisher.isBrokerBackingOff()).isFalse()
        publisher.publishPendingEvents()
        clock.advanceMillis(999)
        assertThat(publisher.isBrokerBackingOff()).isTrue()
        clock.advanceMillis(1)

        assertThat(publisher.isBrokerBackingOff()).isFalse()
        verify(outboxEventRepository, times(1)).markClaimedEventsProcessed(any(), any(), any(), any(), any(), any())
        verify(outboxMetrics, times(2)).recordBrokerFailure()
    }

    @Test
    fun 복구_발행이_브로커_실패면_재시도_횟수를_올리지_않고_선점_시각_조건으로_PENDING으로_되돌린다() {
        val clock = MutableClock(START)
        val publisher = batchClaimPublisher(clock)
        val claimedAt = LocalDateTime.ofInstant(START, clock.zone)
        val event = createOutboxEvent(id = 1L, retryCount = 2)
        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(mock<GenericRecord>())
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        ).thenThrow(KafkaException("Failed to construct kafka producer"))
        whenever(outboxEventRepository.releaseClaimedEvents(any(), any(), any(), any(), any())).thenReturn(1)

        publisher.publishEvent(1L, claimedAt)

        verify(outboxEventRepository).releaseClaimedEvents(
            ids = eq(listOf(1L)),
            pendingStatus = eq(OutboxStatus.PENDING),
            updatedAt = any(),
            claimedStatus = eq(OutboxStatus.RETRYING),
            claimedAt = eq(claimedAt)
        )
        verify(outboxEventRepository, never())
            .markClaimedEventFailed(any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any())
        verify(outboxMetrics, never()).recordOutboxFailed(any())
        assertThat(event.retryCount).isEqualTo(2)
        assertThat(event.processed).isFalse()
        assertThat(publisher.isBrokerBackingOff()).isTrue()
    }

    @Test
    fun 선점_시각_없는_복구_발행이_브로커_실패면_재시도_횟수를_올리지_않고_PENDING으로_save한다() {
        val publisher = batchClaimPublisher(MutableClock(START))
        val event = createOutboxEvent(id = 1L, retryCount = 1)
        whenever(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event))
        whenever(avroEventConverter.convertJsonToAvro(any(), any())).thenReturn(mock<GenericRecord>())
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        ).thenThrow(TimeoutException("Topic payment not present in metadata after 60000 ms."))
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer { it.arguments[0] as OutboxEvent }

        publisher.publishEvent(1L)

        assertThat(event.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(event.retryCount).isEqualTo(1)
        verify(outboxEventRepository).save(eq(event))
        verify(outboxMetrics, never()).recordOutboxFailed(any())
        assertThat(publisher.isBrokerBackingOff()).isTrue()
    }

    private fun batchClaimPublisher(clock: Clock): OutboxEventPublisher =
        OutboxEventPublisher(
            outboxEventRepository, kafkaTemplate, outboxMetrics, avroEventConverter, transactionManager,
            OutboxBrokerBackoff(outboxMetrics, 1_000, 30_000, clock, NoJitter), clock
        ).also {
            ReflectionTestUtils.setField(it, "maxClaimAgeMs", MAX_CLAIM_AGE_MS)
        }

    private fun stubSingleClaim(event: OutboxEvent, onRefetch: () -> Unit = {}) {
        whenever(outboxEventRepository.lockUnprocessedEventIds("PENDING", "FAILED", 3, 100)).thenReturn(listOf(1L))
        whenever(
            outboxEventRepository.claimEventsForPublish(
                ids = eq(listOf(1L)),
                nextStatus = eq("RETRYING"),
                updatedAt = any(),
                pendingStatus = eq("PENDING"),
                failedStatus = eq("FAILED"),
                maxRetries = eq(3)
            )
        ).thenReturn(1)
        whenever(outboxEventRepository.findAllById(listOf(1L))).thenAnswer {
            onRefetch()
            listOf(event)
        }
    }

    private fun runKafkaCallbackDirectly() {
        whenever(
            kafkaTemplate.executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
        ).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as KafkaOperations.OperationsCallback<String, Any, Any>
            callback.doInOperations(kafkaTemplate)
        }
    }

    private fun publisherWith(dataSource: DataSource): OutboxEventPublisher =
        OutboxEventPublisher(
            outboxEventRepository, kafkaTemplate, outboxMetrics, avroEventConverter, transactionManager,
            OutboxBrokerBackoff(outboxMetrics), Clock.systemDefaultZone(), dataSource
        )

    private fun dataSource(productName: String): DataSource {
        val metaData = mock<DatabaseMetaData>()
        whenever(metaData.databaseProductName).thenReturn(productName)
        val connection = mock<Connection>()
        whenever(connection.metaData).thenReturn(metaData)
        val dataSource = mock<DataSource>()
        whenever(dataSource.connection).thenReturn(connection)
        return dataSource
    }

    private fun captureLogs(minimum: Level, action: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger(OutboxEventPublisher::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            action()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
        return appender.list.filter { it.level.isGreaterOrEqual(minimum) }.map { it.formattedMessage }
    }

    private fun captureWarnings(action: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger(OutboxEventPublisher::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            action()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
        return appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
    }

    private object NoJitter : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = 0.0
    }

    private class MutableClock(private var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.systemDefault()
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
        fun advanceMillis(millis: Long) {
            now = now.plusMillis(millis)
        }
    }

    companion object {
        private const val MAX_CLAIM_AGE_MS = 1_000L
        private val START: Instant = Instant.parse("2026-11-20T00:00:00Z")
    }
}
