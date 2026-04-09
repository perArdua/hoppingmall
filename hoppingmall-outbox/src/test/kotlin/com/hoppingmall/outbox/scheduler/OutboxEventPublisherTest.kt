package com.hoppingmall.outbox.scheduler

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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
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

    @InjectMocks
    private lateinit var outboxEventPublisher: OutboxEventPublisher

    private fun createOutboxEvent(
        id: Long = 1L,
        status: OutboxStatus = OutboxStatus.RETRYING,
        retryCount: Int = 0
    ): OutboxEvent {
        val event = OutboxEvent(
            aggregateType = "Payment",
            aggregateId = "1",
            eventType = "PaymentCompleted",
            eventData = """{"paymentId":1}""",
            topic = "payment",
            partitionKey = "1",
            status = status
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
    fun 대기중인_이벤트를_클레임_후_발행한다() {
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
}
