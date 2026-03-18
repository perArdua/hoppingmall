package com.hoppingmall.payment.dlq.service

import com.hoppingmall.payment.dlq.domain.DLQMessage
import com.hoppingmall.payment.dlq.domain.DLQStatus
import com.hoppingmall.payment.dlq.domain.DeadLetterMessage
import com.hoppingmall.payment.dlq.domain.repository.DLQMessageRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.kafka.core.KafkaTemplate
import java.util.*

@DisplayName("DLQService")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class DLQServiceTest {

    @Mock
    private lateinit var dlqMessageRepository: DLQMessageRepository

    @Mock
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @InjectMocks
    private lateinit var dlqService: DLQService

    private fun createDeadLetterMessage(
        topic: String = "test-topic",
        partition: Int = 0,
        offset: Long = 1000L,
        key: String? = "test-key",
        value: String? = "test-value",
        exception: String? = "Test exception",
        timestamp: Long = System.currentTimeMillis()
    ) = DeadLetterMessage(
        originalTopic = topic,
        originalPartition = partition,
        originalOffset = offset,
        originalKey = key,
        originalValue = value,
        exception = exception,
        timestamp = timestamp
    )

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

    @Test
    fun DLQ_메시지_저장_성공() {
        val deadLetterMessage = createDeadLetterMessage()
        whenever(dlqMessageRepository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
            deadLetterMessage.originalTopic,
            deadLetterMessage.originalPartition,
            deadLetterMessage.originalOffset
        )).thenReturn(false)

        dlqService.saveDLQMessage(deadLetterMessage)

        verify(dlqMessageRepository).save(any<DLQMessage>())
    }

    @Test
    fun 중복_DLQ_메시지는_저장하지_않는다() {
        val deadLetterMessage = createDeadLetterMessage()
        whenever(dlqMessageRepository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
            deadLetterMessage.originalTopic,
            deadLetterMessage.originalPartition,
            deadLetterMessage.originalOffset
        )).thenReturn(true)

        dlqService.saveDLQMessage(deadLetterMessage)

        verify(dlqMessageRepository, never()).save(any<DLQMessage>())
    }

    @Test
    fun DLQ_메시지_재처리_성공() {
        val dlqMessageId = 1L
        val dlqMessage = createDLQMessage(
            id = dlqMessageId,
            status = DLQStatus.PENDING,
            retryCount = 1
        )

        whenever(dlqMessageRepository.findById(dlqMessageId))
            .thenReturn(Optional.of(dlqMessage))

        val result = dlqService.retryDLQMessage(dlqMessageId)

        assertTrue(result)
        verify(kafkaTemplate).send(
            eq(dlqMessage.originalTopic),
            eq(dlqMessage.originalKey ?: ""),
            any()
        )
        verify(dlqMessageRepository, times(2)).save(dlqMessage)
        assertEquals(DLQStatus.PROCESSED, dlqMessage.status)
    }

    @Test
    fun 최대_재시도_횟수_초과_시_FAILED_처리() {
        val dlqMessageId = 1L
        val dlqMessage = createDLQMessage(
            id = dlqMessageId,
            status = DLQStatus.PENDING,
            retryCount = 3
        )

        whenever(dlqMessageRepository.findById(dlqMessageId))
            .thenReturn(Optional.of(dlqMessage))

        val result = dlqService.retryDLQMessage(dlqMessageId)

        assertFalse(result)
        assertEquals(DLQStatus.FAILED, dlqMessage.status)
        verify(kafkaTemplate, never()).send(any<String>(), any(), any())
        verify(dlqMessageRepository).save(dlqMessage)
    }
}
