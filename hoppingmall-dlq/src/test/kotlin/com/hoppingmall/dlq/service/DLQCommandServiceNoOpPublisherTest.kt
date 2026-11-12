package com.hoppingmall.dlq.service

import com.hoppingmall.dlq.archival.DLQArchivalService
import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.DeadLetterMessage
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import com.hoppingmall.dlq.metrics.DLQMetrics
import com.hoppingmall.dlq.publisher.NoOpDLQMessagePublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

@DisplayName("DLQCommandService 재발행기 없음")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQCommandServiceNoOpPublisherTest {

    private val dlqMessageRepository: DLQMessageRepository = mock()
    private val dlqMetrics: DLQMetrics = mock()
    private val dlqArchivalService: DLQArchivalService = mock()
    private val dlqCommandService = DLQCommandService(
        dlqMessageRepository,
        NoOpDLQMessagePublisher(),
        dlqMetrics,
        dlqArchivalService
    )

    @Test
    fun 재발행기가_없으면_DLQ_메시지를_바로_FAILED로_저장한다() {
        whenever(dlqMessageRepository.existsByOriginalTopicAndOriginalPartitionAndOriginalOffset(any(), any(), any()))
            .thenReturn(false)

        dlqCommandService.saveDLQMessage(
            DeadLetterMessage(
                originalTopic = "notification",
                originalPartition = 0,
                originalOffset = 7L,
                originalKey = "1",
                originalValue = """{"eventType":"OrderCreatedNotificationRequested","userId":1}""",
                exception = "java.net.SocketTimeoutException: timeout",
                timestamp = System.currentTimeMillis()
            )
        )

        val captor = argumentCaptor<DLQMessage>()
        verify(dlqMessageRepository).save(captor.capture())
        assertThat(captor.lastValue.status).isEqualTo(DLQStatus.FAILED)
        assertThat(captor.lastValue.nextRetryAt).isNull()
    }

    @Test
    fun 재발행기가_없으면_대기_중인_행을_재시도_횟수를_쓰지_않고_FAILED로_옮긴다() {
        val dlqMessage = DLQMessage(
            originalTopic = "notification",
            originalPartition = 0,
            originalOffset = 8L,
            originalKey = "1",
            originalValue = """{"eventType":"OrderCreatedNotificationRequested","userId":1}""",
            exceptionMessage = "java.net.SocketTimeoutException: timeout",
            errorTimestamp = System.currentTimeMillis()
        )
        val idField = DLQMessage::class.java.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(dlqMessage, 1L)
        whenever(dlqMessageRepository.findById(1L)).thenReturn(Optional.of(dlqMessage))

        val result = dlqCommandService.retryDLQMessage(1L)

        assertThat(result).isFalse()
        assertThat(dlqMessage.status).isEqualTo(DLQStatus.FAILED)
        assertThat(dlqMessage.retryCount).isEqualTo(0)
    }
}
