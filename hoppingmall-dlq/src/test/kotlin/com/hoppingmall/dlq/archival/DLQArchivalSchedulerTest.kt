package com.hoppingmall.dlq.archival

import com.hoppingmall.dlq.domain.DLQMessage
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import com.hoppingmall.dlq.domain.repository.DLQ_PURGE_BATCH_SIZE
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl

@DisplayName("DLQArchivalScheduler")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQArchivalSchedulerTest {

    private val dlqMessageRepository: DLQMessageRepository = mock()
    private val dlqArchivalService: DLQArchivalService = mock()
    private val properties = DLQArchivalProperties(
        enabled = true,
        bucket = "test-bucket",
        retentionDays = 90,
        batchSize = 100
    )
    private val scheduler = DLQArchivalScheduler(dlqMessageRepository, dlqArchivalService, properties)

    @Nested
    @DisplayName("archiveFailedMessages")
    inner class ArchiveFailedMessages {

        @Test
        fun FAILED_메시지를_아카이빙하고_archivedAt을_설정한다() {
            val message1 = createDLQMessage(id = 1L, status = DLQStatus.FAILED)
            val message2 = createDLQMessage(id = 2L, status = DLQStatus.FAILED)
            val page = PageImpl(listOf(message1, message2))

            whenever(dlqMessageRepository.findUnarchivedByStatus(eq(DLQStatus.FAILED), any()))
                .thenReturn(page)
            whenever(dlqArchivalService.archive(any())).thenReturn(true)

            scheduler.archiveFailedMessages()

            verify(dlqArchivalService).archive(message1)
            verify(dlqArchivalService).archive(message2)
            verify(dlqMessageRepository).save(message1)
            verify(dlqMessageRepository).save(message2)
            assertNotNull(message1.archivedAt)
            assertNotNull(message2.archivedAt)
        }

        @Test
        fun 아카이빙_대상이_없으면_아무_작업도_하지_않는다() {
            whenever(dlqMessageRepository.findUnarchivedByStatus(eq(DLQStatus.FAILED), any()))
                .thenReturn(Page.empty())

            scheduler.archiveFailedMessages()

            verify(dlqArchivalService, never()).archive(any())
            verify(dlqMessageRepository, never()).save(any<DLQMessage>())
        }

        @Test
        fun 아카이빙_실패한_메시지는_archivedAt을_설정하지_않는다() {
            val message1 = createDLQMessage(id = 1L, status = DLQStatus.FAILED)
            val message2 = createDLQMessage(id = 2L, status = DLQStatus.FAILED)
            val page = PageImpl(listOf(message1, message2))

            whenever(dlqMessageRepository.findUnarchivedByStatus(eq(DLQStatus.FAILED), any()))
                .thenReturn(page)
            whenever(dlqArchivalService.archive(message1)).thenReturn(true)
            whenever(dlqArchivalService.archive(message2)).thenReturn(false)

            scheduler.archiveFailedMessages()

            verify(dlqMessageRepository).save(message1)
            verify(dlqMessageRepository, never()).save(message2)
            assertNotNull(message1.archivedAt)
            assertNull(message2.archivedAt)
        }
    }

    @Nested
    @DisplayName("purgeArchivedMessages")
    inner class PurgeArchivedMessages {

        @Test
        fun 보존기간_초과_아카이빙된_메시지를_퍼지한다() {
            whenever(dlqMessageRepository.deleteArchivedMessagesBefore(eq("FAILED"), any()))
                .thenReturn(1)

            scheduler.purgeArchivedMessages()

            verify(dlqMessageRepository, times(1)).deleteArchivedMessagesBefore(eq("FAILED"), any())
            verify(dlqMessageRepository, never()).deleteAll(any<List<DLQMessage>>())
        }

        @Test
        fun 퍼지_대상이_없으면_한_번만_지운다() {
            whenever(dlqMessageRepository.deleteArchivedMessagesBefore(eq("FAILED"), any()))
                .thenReturn(0)

            scheduler.purgeArchivedMessages()

            verify(dlqMessageRepository, times(1)).deleteArchivedMessagesBefore(eq("FAILED"), any())
        }

        @Test
        fun 배치가_가득_차면_같은_실행에서_이어서_지운다() {
            whenever(dlqMessageRepository.deleteArchivedMessagesBefore(eq("FAILED"), any()))
                .thenReturn(DLQ_PURGE_BATCH_SIZE, DLQ_PURGE_BATCH_SIZE, 3)

            scheduler.purgeArchivedMessages()

            verify(dlqMessageRepository, times(3)).deleteArchivedMessagesBefore(eq("FAILED"), any())
        }

        @Test
        fun 실행당_배치_상한에서_멈춘다() {
            val limitedScheduler = DLQArchivalScheduler(
                dlqMessageRepository,
                dlqArchivalService,
                properties.copy(purgeMaxBatches = 2)
            )
            whenever(dlqMessageRepository.deleteArchivedMessagesBefore(eq("FAILED"), any()))
                .thenReturn(DLQ_PURGE_BATCH_SIZE)

            limitedScheduler.purgeArchivedMessages()

            verify(dlqMessageRepository, times(2)).deleteArchivedMessagesBefore(eq("FAILED"), any())
        }
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
