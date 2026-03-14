package com.hoppingmall.mall.global.common.service

import com.hoppingmall.mall.global.common.config.DeadLetterMessage
import com.hoppingmall.mall.global.common.domain.DLQMessage
import com.hoppingmall.mall.global.common.domain.DLQStatus
import com.hoppingmall.mall.global.common.domain.repository.DLQMessageRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.kafka.core.KafkaTemplate
import java.util.*

@DisplayName("DLQService")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQServiceTest {

    private val dlqMessageRepository: DLQMessageRepository = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val dlqService = DLQService(dlqMessageRepository, kafkaTemplate)

    @Nested
    @DisplayName("saveDLQMessage")
    inner class SaveDLQMessage {
        
        @Test
        fun 중복되지_않은_DLQ_메시지를_저장한다() {
            // given
            val deadLetterMessage = createDeadLetterMessage()
            whenever(dlqMessageRepository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
                deadLetterMessage.originalTopic,
                deadLetterMessage.originalPartition,
                deadLetterMessage.originalOffset
            )).thenReturn(false)
            
            // when
            dlqService.saveDLQMessage(deadLetterMessage)
            
            // then
            verify(dlqMessageRepository).save(any<DLQMessage>())
        }
        
        @Test
        fun 비재시도_에러는_즉시_FAILED로_저장한다() {
            // given
            val deadLetterMessage = createDeadLetterMessage(
                exception = "org.apache.kafka.common.errors.DeserializationException: Error"
            )
            whenever(dlqMessageRepository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
                deadLetterMessage.originalTopic,
                deadLetterMessage.originalPartition,
                deadLetterMessage.originalOffset
            )).thenReturn(false)

            // when
            dlqService.saveDLQMessage(deadLetterMessage)

            // then
            verify(dlqMessageRepository).save(argThat<DLQMessage> {
                this.status == DLQStatus.FAILED && this.nextRetryAt == null
            })
        }

        @Test
        fun 재시도_가능한_에러는_nextRetryAt을_설정하여_저장한다() {
            // given
            val deadLetterMessage = createDeadLetterMessage(
                exception = "java.net.SocketTimeoutException: Connection timed out"
            )
            whenever(dlqMessageRepository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
                deadLetterMessage.originalTopic,
                deadLetterMessage.originalPartition,
                deadLetterMessage.originalOffset
            )).thenReturn(false)

            // when
            dlqService.saveDLQMessage(deadLetterMessage)

            // then
            verify(dlqMessageRepository).save(argThat<DLQMessage> {
                this.status == DLQStatus.PENDING && this.nextRetryAt != null
            })
        }

        @Test
        fun 중복된_DLQ_메시지는_저장하지_않는다() {
            // given
            val deadLetterMessage = createDeadLetterMessage()
            whenever(dlqMessageRepository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(
                deadLetterMessage.originalTopic,
                deadLetterMessage.originalPartition,
                deadLetterMessage.originalOffset
            )).thenReturn(true)
            
            // when
            dlqService.saveDLQMessage(deadLetterMessage)
            
            // then
            verify(dlqMessageRepository, never()).save(any<DLQMessage>())
        }
    }
    
    @Nested
    @DisplayName("getDLQMessages")
    inner class GetDLQMessages {
        
        @Test
        fun 토픽별_DLQ_메시지를_페이징하여_조회한다() {
            // given
            val topic = "payment"
            val page = 0
            val size = 20
            val expectedMessages = listOf(createDLQMessage(topic = topic))
            val expectedPage = PageImpl(expectedMessages, PageRequest.of(page, size), 1)
            
            whenever(dlqMessageRepository.findByOriginalTopicOrderByCreatedAtDesc(
                eq(topic), any()
            )).thenReturn(expectedPage)
            
            // when
            val result = dlqService.getDLQMessages(topic, page, size)
            
            // then
            assertEquals(expectedPage, result)
            verify(dlqMessageRepository).findByOriginalTopicOrderByCreatedAtDesc(
                eq(topic), eq(PageRequest.of(page, size))
            )
        }
    }
    
    @Nested
    @DisplayName("getDLQMessagesByStatus")
    inner class GetDLQMessagesByStatus {
        
        @Test
        fun 상태별_DLQ_메시지를_페이징하여_조회한다() {
            // given
            val status = DLQStatus.PENDING
            val page = 0
            val size = 20
            val expectedMessages = listOf(createDLQMessage(status = status))
            val expectedPage = PageImpl(expectedMessages, PageRequest.of(page, size), 1)
            
            whenever(dlqMessageRepository.findByStatusOrderByCreatedAtDesc(
                eq(status), any()
            )).thenReturn(expectedPage)
            
            // when
            val result = dlqService.getDLQMessagesByStatus(status, page, size)
            
            // then
            assertEquals(expectedPage, result)
            verify(dlqMessageRepository).findByStatusOrderByCreatedAtDesc(
                eq(status), eq(PageRequest.of(page, size))
            )
        }
    }
    
    @Nested
    @DisplayName("retryDLQMessage")
    inner class RetryDLQMessage {
        
        @Test
        fun PENDING_상태의_메시지를_성공적으로_재처리한다() {
            // given
            val dlqMessageId = 1L
            val dlqMessage = createDLQMessage(
                id = dlqMessageId,
                status = DLQStatus.PENDING,
                retryCount = 1
            )
            
            whenever(dlqMessageRepository.findById(dlqMessageId))
                .thenReturn(Optional.of(dlqMessage))
            
            // when
            val result = dlqService.retryDLQMessage(dlqMessageId)
            
            // then
            assertTrue(result)
            verify(kafkaTemplate).send(
                eq(dlqMessage.originalTopic),
                eq(dlqMessage.originalKey ?: ""),
                any()
            )
            verify(dlqMessageRepository, times(2)).save(dlqMessage) // incrementRetry + markAsProcessed
            assertEquals(DLQStatus.PROCESSED, dlqMessage.status)
        }
        
        @Test
        fun 존재하지_않는_메시지_ID로_재처리_시_false를_반환한다() {
            // given
            val dlqMessageId = 999L
            whenever(dlqMessageRepository.findById(dlqMessageId))
                .thenReturn(Optional.empty())
            
            // when
            val result = dlqService.retryDLQMessage(dlqMessageId)
            
            // then
            assertFalse(result)
            verify(kafkaTemplate, never()).send(any(), any(), any())
        }
        
        @Test
        fun PENDING이_아닌_상태의_메시지는_재처리하지_않는다() {
            // given
            val dlqMessageId = 1L
            val dlqMessage = createDLQMessage(
                id = dlqMessageId,
                status = DLQStatus.PROCESSED
            )
            
            whenever(dlqMessageRepository.findById(dlqMessageId))
                .thenReturn(Optional.of(dlqMessage))
            
            // when
            val result = dlqService.retryDLQMessage(dlqMessageId)
            
            // then
            assertFalse(result)
            verify(kafkaTemplate, never()).send(any(), any(), any())
        }
        
        @Test
        fun 최대_재시도_횟수를_초과한_메시지는_FAILED로_처리한다() {
            // given
            val dlqMessageId = 1L
            val dlqMessage = createDLQMessage(
                id = dlqMessageId,
                status = DLQStatus.PENDING,
                retryCount = 3 // 최대 재시도 횟수 초과
            )
            
            whenever(dlqMessageRepository.findById(dlqMessageId))
                .thenReturn(Optional.of(dlqMessage))
            
            // when
            val result = dlqService.retryDLQMessage(dlqMessageId)
            
            // then
            assertFalse(result)
            assertEquals(DLQStatus.FAILED, dlqMessage.status)
            verify(kafkaTemplate, never()).send(any(), any(), any())
            verify(dlqMessageRepository).save(dlqMessage)
        }
        
        @Test
        fun Kafka_전송_실패_시_재시도_가능하면_다음_재시도를_스케줄링한다() {
            // given
            val dlqMessageId = 1L
            val dlqMessage = createDLQMessage(
                id = dlqMessageId,
                status = DLQStatus.PENDING,
                retryCount = 1
            )

            whenever(dlqMessageRepository.findById(dlqMessageId))
                .thenReturn(Optional.of(dlqMessage))
            whenever(kafkaTemplate.send(any<String>(), any(), any()))
                .thenThrow(RuntimeException("Kafka 전송 실패"))

            // when
            val result = dlqService.retryDLQMessage(dlqMessageId)

            // then
            assertFalse(result)
            assertEquals(DLQStatus.PENDING, dlqMessage.status)
            assertNotNull(dlqMessage.nextRetryAt)
            verify(dlqMessageRepository, atLeast(2)).save(dlqMessage)
        }

        @Test
        fun Kafka_전송_실패_시_최대_재시도_초과하면_FAILED로_처리한다() {
            // given
            val dlqMessageId = 1L
            val dlqMessage = createDLQMessage(
                id = dlqMessageId,
                status = DLQStatus.PENDING,
                retryCount = 2
            )

            whenever(dlqMessageRepository.findById(dlqMessageId))
                .thenReturn(Optional.of(dlqMessage))
            whenever(kafkaTemplate.send(any<String>(), any(), any()))
                .thenThrow(RuntimeException("Kafka 전송 실패"))

            // when
            val result = dlqService.retryDLQMessage(dlqMessageId)

            // then
            assertFalse(result)
            assertEquals(DLQStatus.FAILED, dlqMessage.status)
            verify(dlqMessageRepository, atLeast(2)).save(dlqMessage)
        }
    }
    
    @Nested
    @DisplayName("retryDLQMessagesByTopic")
    inner class RetryDLQMessagesByTopic {
        
        @Test
        fun 토픽별_DLQ_메시지를_일괄_재처리한다() {
            // given
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
            
            // when
            val result = dlqService.retryDLQMessagesByTopic(topic, maxCount)
            
            // then
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
            // given
            val topic = "payment"
            val processedMessage1 = createDLQMessage(topic = topic, status = DLQStatus.PROCESSED)
            val processedMessage2 = createDLQMessage(topic = topic, status = DLQStatus.PROCESSED)
            val processedMessages = PageImpl(listOf(processedMessage1, processedMessage2))
            
            whenever(dlqMessageRepository.findByOriginalTopicAndStatusOrderByCreatedAtDesc(
                eq(topic), eq(DLQStatus.PROCESSED), any()
            )).thenReturn(processedMessages)
            
            // when
            val deletedCount = dlqService.clearProcessedDLQMessages(topic)
            
            // then
            assertEquals(2, deletedCount)
            verify(dlqMessageRepository).deleteAll(processedMessages.content)
        }
    }
    
    @Nested
    @DisplayName("getDLQStats")
    inner class GetDLQStats {
        
        @Test
        fun DLQ_통계를_조회한다() {
            // given
            whenever(dlqMessageRepository.count()).thenReturn(10)
            whenever(dlqMessageRepository.countByStatus(DLQStatus.PENDING)).thenReturn(5)
            whenever(dlqMessageRepository.countByStatus(DLQStatus.PROCESSED)).thenReturn(3)
            whenever(dlqMessageRepository.countByStatus(DLQStatus.FAILED)).thenReturn(2)
            whenever(dlqMessageRepository.getDLQStatsByTopic()).thenReturn(emptyList())
            
            // when
            val stats = dlqService.getDLQStats()
            
            // then
            assertEquals(10L, stats["totalMessages"])
            assertEquals(5L, stats["pendingCount"])
            assertEquals(3L, stats["processedCount"])
            assertEquals(2L, stats["failedCount"])
            assertNotNull(stats["lastUpdated"])
            assertNotNull(stats["topicStats"])
        }
    }
    
    @Nested
    @DisplayName("autoRetryPendingMessages")
    inner class AutoRetryPendingMessages {

        @Test
        fun 재시도_대상_메시지를_자동으로_재처리한다() {
            // given
            val dlqMessage = createDLQMessage(
                id = 1L,
                status = DLQStatus.PENDING,
                retryCount = 0
            ).apply { nextRetryAt = System.currentTimeMillis() - 1000 }

            val page = PageImpl(listOf(dlqMessage))
            whenever(dlqMessageRepository.findAutoRetryableMessages(
                eq(DLQStatus.PENDING), eq(3), any(), any()
            )).thenReturn(page)
            whenever(dlqMessageRepository.findById(1L)).thenReturn(Optional.of(dlqMessage))

            // when
            dlqService.autoRetryPendingMessages()

            // then
            verify(kafkaTemplate).send(
                eq(dlqMessage.originalTopic),
                eq(dlqMessage.originalKey ?: ""),
                any()
            )
        }

        @Test
        fun 재시도_대상이_없으면_아무_작업도_하지_않는다() {
            // given
            whenever(dlqMessageRepository.findAutoRetryableMessages(
                eq(DLQStatus.PENDING), eq(3), any(), any()
            )).thenReturn(Page.empty())

            // when
            dlqService.autoRetryPendingMessages()

            // then
            verify(kafkaTemplate, never()).send(any<String>(), any(), any())
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