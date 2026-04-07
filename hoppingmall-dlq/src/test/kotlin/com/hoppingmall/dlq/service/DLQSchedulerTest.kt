package com.hoppingmall.dlq.service

import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl

@DisplayName("DLQScheduler")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQSchedulerTest {

    private val dlqMessageRepository: DLQMessageRepository = mock()
    private val dlqCommandService: DLQCommandService = mock()
    private val dlqScheduler = DLQScheduler(dlqMessageRepository, dlqCommandService)

    @Nested
    @DisplayName("autoRetryPendingMessages")
    inner class AutoRetryPendingMessages {

        @Test
        fun 재시도_대상_메시지를_자동으로_재처리한다() {
            val dlqMessage = createDLQMessage(
                id = 1L,
                status = DLQStatus.PENDING,
                retryCount = 0
            ).apply { nextRetryAt = System.currentTimeMillis() - 1000 }

            val page = PageImpl(listOf(dlqMessage))
            whenever(dlqMessageRepository.findAutoRetryableMessages(
                eq(DLQStatus.PENDING), eq(3), any(), any()
            )).thenReturn(page)
            whenever(dlqCommandService.retryDLQMessage(1L)).thenReturn(true)

            dlqScheduler.autoRetryPendingMessages()

            verify(dlqCommandService).retryDLQMessage(1L)
        }

        @Test
        fun 재시도_대상이_없으면_아무_작업도_하지_않는다() {
            whenever(dlqMessageRepository.findAutoRetryableMessages(
                eq(DLQStatus.PENDING), eq(3), any(), any()
            )).thenReturn(Page.empty())

            dlqScheduler.autoRetryPendingMessages()

            verify(dlqCommandService, never()).retryDLQMessage(any())
        }

        @Test
        fun 여러_메시지를_배치로_재처리한다() {
            val dlqMessage1 = createDLQMessage(id = 1L, status = DLQStatus.PENDING, retryCount = 0)
            val dlqMessage2 = createDLQMessage(id = 2L, status = DLQStatus.PENDING, retryCount = 1)
            val dlqMessage3 = createDLQMessage(id = 3L, status = DLQStatus.PENDING, retryCount = 2)

            val page = PageImpl(listOf(dlqMessage1, dlqMessage2, dlqMessage3))
            whenever(dlqMessageRepository.findAutoRetryableMessages(
                eq(DLQStatus.PENDING), eq(3), any(), any()
            )).thenReturn(page)
            whenever(dlqCommandService.retryDLQMessage(1L)).thenReturn(true)
            whenever(dlqCommandService.retryDLQMessage(2L)).thenReturn(true)
            whenever(dlqCommandService.retryDLQMessage(3L)).thenReturn(false)

            dlqScheduler.autoRetryPendingMessages()

            verify(dlqCommandService).retryDLQMessage(1L)
            verify(dlqCommandService).retryDLQMessage(2L)
            verify(dlqCommandService).retryDLQMessage(3L)
        }
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
