package com.hoppingmall.dlq.archival

import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("NoOpDLQArchivalService")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NoOpDLQArchivalServiceTest {

    private val archivalService = NoOpDLQArchivalService()

    @Test
    fun archive_호출하면_false를_반환한다() {
        val dlqMessage = createDLQMessage()

        val result = archivalService.archive(dlqMessage)

        assertThat(result).isFalse()
    }

    @Test
    fun archiveBatch_호출하면_0을_반환한다() {
        val messages = listOf(createDLQMessage(), createDLQMessage())

        val result = archivalService.archiveBatch(messages)

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun archiveBatch_빈_리스트도_0을_반환한다() {
        val result = archivalService.archiveBatch(emptyList())

        assertThat(result).isEqualTo(0)
    }

    private fun createDLQMessage(
        id: Long? = 1L,
        status: DLQStatus = DLQStatus.FAILED
    ): DLQMessage {
        val dlqMessage = DLQMessage(
            originalTopic = "test-topic",
            originalPartition = 0,
            originalOffset = 1000L,
            originalKey = "test-key",
            originalValue = "test-value",
            exceptionMessage = "Test exception",
            errorTimestamp = System.currentTimeMillis()
        ).apply {
            this.status = status
        }

        id?.let {
            val idField = DLQMessage::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(dlqMessage, it)
        }

        return dlqMessage
    }
}
