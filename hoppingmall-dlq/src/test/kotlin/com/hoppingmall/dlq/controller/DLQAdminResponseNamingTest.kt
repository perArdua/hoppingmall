package com.hoppingmall.dlq.controller

import com.hoppingmall.common.GlobalExceptionHandler
import com.hoppingmall.dlq.archival.DLQArchivalService
import com.hoppingmall.dlq.domain.DLQStatus
import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import com.hoppingmall.dlq.domain.repository.DLQStatsProjection
import com.hoppingmall.dlq.metrics.DLQMetrics
import com.hoppingmall.dlq.publisher.DLQMessagePublisher
import com.hoppingmall.dlq.service.DLQCommandService
import com.hoppingmall.dlq.service.DLQQueryService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@DisplayName("DLQ 관리 통계·일괄 재처리 응답 이름")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQAdminResponseNamingTest {

    private val repository: DLQMessageRepository = mock()
    private val commandService = DLQCommandService(repository, mock<DLQMessagePublisher>(), mock<DLQMetrics>(), mock<DLQArchivalService>())
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(DLQController(commandService, DLQQueryService(repository)))
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @Test
    fun 통계는_snake_case_이름으로_나간다() {
        whenever(repository.count()).thenReturn(7L)
        whenever(repository.countByStatus(DLQStatus.PENDING)).thenReturn(4L)
        whenever(repository.countByStatus(DLQStatus.PROCESSED)).thenReturn(2L)
        whenever(repository.countByStatus(DLQStatus.FAILED)).thenReturn(1L)
        whenever(repository.getDLQStatsByTopic()).thenReturn(listOf(TopicStats("payment", 7L, 4L, 2L, 1L)))

        mockMvc.get("/api/v1/admin/dlq/stats").andExpect {
            status { isOk() }
            jsonPath("$.data.total_messages") { value(7) }
            jsonPath("$.data.pending_count") { value(4) }
            jsonPath("$.data.processed_count") { value(2) }
            jsonPath("$.data.failed_count") { value(1) }
            jsonPath("$.data.last_updated") { exists() }
            jsonPath("$.data.topic_stats[0].topic") { value("payment") }
            jsonPath("$.data.topic_stats[0].total") { value(7) }
            jsonPath("$.data.topic_stats[0].pending") { value(4) }
            jsonPath("$.data.topic_stats[0].processed") { value(2) }
            jsonPath("$.data.topic_stats[0].failed") { value(1) }
            jsonPath("$.data.totalMessages") { doesNotExist() }
        }
    }

    @Test
    fun 토픽_일괄_재처리_결과는_snake_case_이름으로_나간다() {
        whenever(repository.findByOriginalTopicAndStatusOrderByCreatedAtDesc(eq("payment"), eq(DLQStatus.PENDING), any()))
            .thenReturn(PageImpl(emptyList()))

        mockMvc.post("/api/v1/admin/dlq/retry/payment").andExpect {
            status { isOk() }
            jsonPath("$.data.topic") { value("payment") }
            jsonPath("$.data.total_attempted") { value(0) }
            jsonPath("$.data.success_count") { value(0) }
            jsonPath("$.data.failure_count") { value(0) }
            jsonPath("$.data.errors") { isEmpty() }
            jsonPath("$.data.totalAttempted") { doesNotExist() }
        }
    }

    private data class TopicStats(
        override val topic: String,
        override val totalCount: Long,
        override val pendingCount: Long,
        override val processedCount: Long,
        override val failedCount: Long
    ) : DLQStatsProjection
}
