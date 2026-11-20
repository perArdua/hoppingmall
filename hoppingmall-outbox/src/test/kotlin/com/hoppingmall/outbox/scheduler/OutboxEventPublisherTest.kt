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
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import java.util.concurrent.CompletableFuture

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
            outboxEventRepository, kafkaTemplate, outboxMetrics, avroEventConverter, transactionManager
        )
    }

    private fun createOutboxEvent(
        id: Long = 1L,
        status: OutboxStatus = OutboxStatus.RETRYING,
        retryCount: Int = 0,
        createdAt: LocalDateTime = LocalDateTime.now()
    ): OutboxEvent {
        val event = OutboxEvent(
            aggregateType = "Payment",
            aggregateId = "1",
            eventType = "PaymentCompleted",
            eventData = """{"paymentId":1}""",
            topic = "payment",
            partitionKey = "1",
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
    fun 배치_발행_실패_시_모든_이벤트를_실패_처리한다() {
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
        ).thenThrow(RuntimeException("Kafka transaction failed"))
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer { it.arguments[0] as OutboxEvent }

        outboxEventPublisher.publishPendingEvents()

        assertThat(retryingEvent.status).isEqualTo(OutboxStatus.FAILED)
        verify(outboxMetrics).recordOutboxFailed("payment")
    }

    @Test
    fun 일괄_선점_모드에서는_잠근_행을_한_번에_다시_읽어_생성_순서대로_발행한다() {
        ReflectionTestUtils.setField(outboxEventPublisher, "batchClaim", true)
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
        ReflectionTestUtils.setField(outboxEventPublisher, "batchClaim", true)
        whenever(outboxEventRepository.lockUnprocessedEventIds("PENDING", "FAILED", 3, 100)).thenReturn(emptyList())

        outboxEventPublisher.publishPendingEvents()

        verify(outboxEventRepository, never()).claimEventsForPublish(any(), any(), any(), any(), any(), any())
        verify(outboxEventRepository, never()).findAllById(any())
    }

    @Test
    fun 일괄_선점_갱신_수가_잠근_행_수와_달라도_잠근_행만_다시_읽는다() {
        ReflectionTestUtils.setField(outboxEventPublisher, "batchClaim", true)
        val notRetrying = createOutboxEvent(id = 1L, status = OutboxStatus.PENDING)

        whenever(outboxEventRepository.lockUnprocessedEventIds("PENDING", "FAILED", 3, 100)).thenReturn(listOf(1L))
        whenever(outboxEventRepository.claimEventsForPublish(any(), any(), any(), any(), any(), any())).thenReturn(0)
        whenever(outboxEventRepository.findAllById(listOf(1L))).thenReturn(listOf(notRetrying))

        outboxEventPublisher.publishPendingEvents()

        verify(avroEventConverter, never()).convertJsonToAvro(any(), any())
        verify(kafkaTemplate, never()).executeInTransaction(any<KafkaOperations.OperationsCallback<String, Any, Any>>())
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

    private fun batchClaimPublisher(clock: Clock): OutboxEventPublisher =
        OutboxEventPublisher(
            outboxEventRepository, kafkaTemplate, outboxMetrics, avroEventConverter, transactionManager, clock
        ).also {
            ReflectionTestUtils.setField(it, "batchClaim", true)
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

    private class MutableClock(private var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.systemDefault()
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
        fun advanceMillis(millis: Long) {
            now = now.plusMillis(millis)
        }
    }

    companion object {
        private val START: Instant = Instant.parse("2026-11-20T00:00:00Z")
    }
}
