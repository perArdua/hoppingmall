package com.hoppingmall.dlq.service

import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.DeadLetterMessage
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import com.hoppingmall.dlq.metrics.DLQMetrics
import com.hoppingmall.dlq.publisher.DLQMessagePublisher
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.*

@DisplayName("DLQCommandService")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQCommandServiceTest {

    private val dlqMessageRepository: DLQMessageRepository = mock()
    private val dlqMessagePublisher: DLQMessagePublisher = mock()
    private val dlqMetrics: DLQMetrics = mock()
    private val dlqCommandService = DLQCommandService(dlqMessageRepository, dlqMessagePublisher, dlqMetrics)

    @Nested
    @DisplayName("saveDLQMessage")
    inner class SaveDLQMessage {

        @Test
        fun 중복되지_않은_DLQ_메시지를_저장한다() {
            val deadLetterMessage = createDeadLetterMessage()
            whenever(dlqMessageRepository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
                deadLetterMessage.originalTopic,
                deadLetterMessage.originalPartition,
                deadLetterMessage.originalOffset
            )).thenReturn(false)

            dlqCommandService.saveDLQMessage(deadLetterMessage)

            verify(dlqMessageRepository).save(any<DLQMessage>())
            verify(dlqMetrics).recordDlqSaved(deadLetterMessage.originalTopic)
        }

        @Test
        fun 비재시도_에러는_즉시_FAILED로_저장한다() {
            val deadLetterMessage = createDeadLetterMessage(
                exception = "org.apache.kafka.common.errors.DeserializationException: Error"
            )
            whenever(dlqMessageRepository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
                deadLetterMessage.originalTopic,
                deadLetterMessage.originalPartition,
                deadLetterMessage.originalOffset
            )).thenReturn(false)

            dlqCommandService.saveDLQMessage(deadLetterMessage)

            verify(dlqMessageRepository).save(argThat<DLQMessage> {
                this.status == DLQStatus.FAILED && this.nextRetryAt == null
            })
            verify(dlqMetrics, never()).recordDlqSaved(any())
        }

        @Test
        fun 재시도_가능한_에러는_nextRetryAt을_설정하여_저장한다() {
            val deadLetterMessage = createDeadLetterMessage(
                exception = "java.net.SocketTimeoutException: Connection timed out"
            )
            whenever(dlqMessageRepository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
                deadLetterMessage.originalTopic,
                deadLetterMessage.originalPartition,
                deadLetterMessage.originalOffset
            )).thenReturn(false)

            dlqCommandService.saveDLQMessage(deadLetterMessage)

            verify(dlqMessageRepository).save(argThat<DLQMessage> {
                this.status == DLQStatus.PENDING && this.nextRetryAt != null
            })
        }

        @Test
        fun 중복된_DLQ_메시지는_저장하지_않는다() {
            val deadLetterMessage = createDeadLetterMessage()
            whenever(dlqMessageRepository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
                deadLetterMessage.originalTopic,
                deadLetterMessage.originalPartition,
                deadLetterMessage.originalOffset
            )).thenReturn(true)

            dlqCommandService.saveDLQMessage(deadLetterMessage)

            verify(dlqMessageRepository, never()).save(any<DLQMessage>())
        }
    }

    @Nested
    @DisplayName("retryDLQMessage")
    inner class RetryDLQMessage {

        @Test
        fun PENDING_상태의_메시지를_성공적으로_재처리한다() {
            val dlqMessageId = 1L
            val dlqMessage = createDLQMessage(
                id = dlqMessageId,
                status = DLQStatus.PENDING,
                retryCount = 1
            )

            whenever(dlqMessageRepository.findById(dlqMessageId))
                .thenReturn(Optional.of(dlqMessage))
            whenever(dlqMessagePublisher.publish(any(), any(), any()))
                .thenReturn(true)

            val result = dlqCommandService.retryDLQMessage(dlqMessageId)

            assertTrue(result)
            verify(dlqMessagePublisher).publish(
                eq(dlqMessage.originalTopic),
                eq(dlqMessage.originalKey ?: ""),
                any()
            )
            verify(dlqMessageRepository, atLeast(2)).save(dlqMessage)
            assertEquals(DLQStatus.PROCESSED, dlqMessage.status)
            verify(dlqMetrics).recordDlqRetrySuccess()
        }

        @Test
        fun 존재하지_않는_메시지_ID로_재처리_시_false를_반환한다() {
            val dlqMessageId = 999L
            whenever(dlqMessageRepository.findById(dlqMessageId))
                .thenReturn(Optional.empty())

            val result = dlqCommandService.retryDLQMessage(dlqMessageId)

            assertFalse(result)
            verify(dlqMessagePublisher, never()).publish(any(), any(), any())
        }

        @Test
        fun PENDING이_아닌_상태의_메시지는_재처리하지_않는다() {
            val dlqMessageId = 1L
            val dlqMessage = createDLQMessage(
                id = dlqMessageId,
                status = DLQStatus.PROCESSED
            )

            whenever(dlqMessageRepository.findById(dlqMessageId))
                .thenReturn(Optional.of(dlqMessage))

            val result = dlqCommandService.retryDLQMessage(dlqMessageId)

            assertFalse(result)
            verify(dlqMessagePublisher, never()).publish(any(), any(), any())
        }

        @Test
        fun 최대_재시도_횟수를_초과한_메시지는_FAILED로_처리한다() {
            val dlqMessageId = 1L
            val dlqMessage = createDLQMessage(
                id = dlqMessageId,
                status = DLQStatus.PENDING,
                retryCount = 3
            )

            whenever(dlqMessageRepository.findById(dlqMessageId))
                .thenReturn(Optional.of(dlqMessage))

            val result = dlqCommandService.retryDLQMessage(dlqMessageId)

            assertFalse(result)
            assertEquals(DLQStatus.FAILED, dlqMessage.status)
            verify(dlqMessagePublisher, never()).publish(any(), any(), any())
            verify(dlqMessageRepository).save(dlqMessage)
        }

        @Test
        fun Publisher가_false를_반환하면_다음_재시도를_스케줄링한다() {
            val dlqMessageId = 1L
            val dlqMessage = createDLQMessage(
                id = dlqMessageId,
                status = DLQStatus.PENDING,
                retryCount = 1
            )

            whenever(dlqMessageRepository.findById(dlqMessageId))
                .thenReturn(Optional.of(dlqMessage))
            whenever(dlqMessagePublisher.publish(any(), any(), any()))
                .thenReturn(false)

            val result = dlqCommandService.retryDLQMessage(dlqMessageId)

            assertFalse(result)
            assertEquals(DLQStatus.PENDING, dlqMessage.status)
            assertNotNull(dlqMessage.nextRetryAt)
        }

        @Test
        fun 발행_예외_시_재시도_가능하면_다음_재시도를_스케줄링한다() {
            val dlqMessageId = 1L
            val dlqMessage = createDLQMessage(
                id = dlqMessageId,
                status = DLQStatus.PENDING,
                retryCount = 1
            )

            whenever(dlqMessageRepository.findById(dlqMessageId))
                .thenReturn(Optional.of(dlqMessage))
            whenever(dlqMessagePublisher.publish(any(), any(), any()))
                .thenThrow(RuntimeException("Kafka 전송 실패"))

            val result = dlqCommandService.retryDLQMessage(dlqMessageId)

            assertFalse(result)
            verify(dlqMetrics).recordDlqRetryFailed()
        }

        @Test
        fun 발행_예외_시_최대_재시도_초과하면_FAILED로_처리한다() {
            val dlqMessageId = 1L
            val dlqMessage = createDLQMessage(
                id = dlqMessageId,
                status = DLQStatus.PENDING,
                retryCount = 2
            )

            whenever(dlqMessageRepository.findById(dlqMessageId))
                .thenReturn(Optional.of(dlqMessage))
            whenever(dlqMessagePublisher.publish(any(), any(), any()))
                .thenThrow(RuntimeException("Kafka 전송 실패"))

            val result = dlqCommandService.retryDLQMessage(dlqMessageId)

            assertFalse(result)
            verify(dlqMetrics).recordDlqRetryFailed()
        }
    }

    @Nested
    @DisplayName("retryDLQMessagesByTopic")
    inner class RetryDLQMessagesByTopic {

        @Test
        fun 토픽별_DLQ_메시지를_일괄_재처리한다() {
            val topic = "payment"
            val maxCount = 50
            val dlqMessage1 = createDLQMessage(id = 1L, topic = topic, status = DLQStatus.PENDING)
            val dlqMessage2 = createDLQMessage(id = 2L, topic = topic, status = DLQStatus.PENDING)
            val pendingMessages = PageImpl(listOf(dlqMessage1, dlqMessage2))

            whenever(dlqMessageRepository.findByOriginalTopicAndStatusOrderByCreatedAtDesc(
                eq(topic), eq(DLQStatus.PENDING), any()
            )).thenReturn(pendingMessages)

            whenever(dlqMessageRepository.findById(1L)).thenReturn(Optional.of(dlqMessage1))
            whenever(dlqMessageRepository.findById(2L)).thenReturn(Optional.of(dlqMessage2))
            whenever(dlqMessagePublisher.publish(any(), any(), any())).thenReturn(true)

            val result = dlqCommandService.retryDLQMessagesByTopic(topic, maxCount)

            assertEquals(topic, result["topic"])
            assertEquals(2, result["totalAttempted"])
            assertEquals(2, result["successCount"])
            assertEquals(0, result["failureCount"])
        }
    }

    @Nested
    @DisplayName("clearProcessedDLQMessages")
    inner class ClearProcessedDLQMessages {

        @Test
        fun 처리_완료된_DLQ_메시지들을_삭제한다() {
            val topic = "payment"
            val processedMessage1 = createDLQMessage(topic = topic, status = DLQStatus.PROCESSED)
            val processedMessage2 = createDLQMessage(topic = topic, status = DLQStatus.PROCESSED)
            val processedMessages = PageImpl(listOf(processedMessage1, processedMessage2))

            whenever(dlqMessageRepository.findByOriginalTopicAndStatusOrderByCreatedAtDesc(
                eq(topic), eq(DLQStatus.PROCESSED), any()
            )).thenReturn(processedMessages)

            val deletedCount = dlqCommandService.clearProcessedDLQMessages(topic)

            assertEquals(2, deletedCount)
            verify(dlqMessageRepository).deleteAll(processedMessages.content)
        }
    }

    @Nested
    @DisplayName("reconstructOriginalMessage")
    inner class ReconstructOriginalMessage {

        @Test
        fun JSON_객체_형태의_원본_메시지를_그대로_반환한다() {
            val dlqMessage = createDLQMessage(value = """{"orderId": 1}""")

            val result = dlqCommandService.reconstructOriginalMessage(dlqMessage)

            assertEquals("""{"orderId": 1}""", result)
        }

        @Test
        fun JSON_배열_형태의_원본_메시지를_그대로_반환한다() {
            val dlqMessage = createDLQMessage(value = """[1, 2, 3]""")

            val result = dlqCommandService.reconstructOriginalMessage(dlqMessage)

            assertEquals("""[1, 2, 3]""", result)
        }

        @Test
        fun null_값은_null을_반환한다() {
            val dlqMessage = createDLQMessage(value = null)

            val result = dlqCommandService.reconstructOriginalMessage(dlqMessage)

            assertNull(result)
        }
    }

    private fun createDeadLetterMessage(
        topic: String = "test-topic",
        partition: Int = 0,
        offset: Long = 1000L,
        key: String? = "test-key",
        value: String? = "test-value",
        exception: String? = "Test exception",
        timestamp: Long = System.currentTimeMillis()
    ): DeadLetterMessage {
        return DeadLetterMessage(
            originalTopic = topic,
            originalPartition = partition,
            originalOffset = offset,
            originalKey = key,
            originalValue = value,
            exception = exception,
            timestamp = timestamp
        )
    }

    private fun createDLQMessage(
        id: Long? = 1L,
        topic: String = "test-topic",
        partition: Int = 0,
        offset: Long = 1000L,
        key: String? = "test-key",
        value: String? = "test-value",
        exception: String? = "Test exception",
        timestamp: Long = System.currentTimeMillis(),
        status: DLQStatus = DLQStatus.PENDING,
        retryCount: Int = 0
    ): DLQMessage {
        val dlqMessage = DLQMessage(
            originalTopic = topic,
            originalPartition = partition,
            originalOffset = offset,
            originalKey = key,
            originalValue = value,
            exceptionMessage = exception,
            errorTimestamp = timestamp
        ).apply {
            this.status = status
            this.retryCount = retryCount
        }

        id?.let {
            val idField = DLQMessage::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(dlqMessage, it)
        }

        return dlqMessage
    }
}
