package com.hoppingmall.mall.global.common.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.global.common.domain.DLQMessage
import com.hoppingmall.mall.global.common.domain.DLQStatus
import com.hoppingmall.mall.global.common.service.DLQService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(DLQController::class)
@DisplayName("DLQController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var dlqService: DLQService

    @Nested
    @DisplayName("GET /api/v1/admin/dlq/stats")
    inner class GetDLQStats {
        
        @Test
        @WithMockUser(roles = ["ADMIN"])
        fun DLQ_통계를_조회한다() {
            // given
            val expectedStats = mapOf(
                "totalMessages" to 100L,
                "pendingCount" to 50L,
                "processedCount" to 30L,
                "failedCount" to 20L,
                "topicStats" to emptyList<Map<String, Any>>(),
                "lastUpdated" to System.currentTimeMillis()
            )
            
            whenever(dlqService.getDLQStats()).thenReturn(expectedStats)
            
            // when & then
            mockMvc.perform(get("/api/v1/admin/dlq/stats"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalMessages").value(100))
                .andExpect(jsonPath("$.data.pendingCount").value(50))
                .andExpect(jsonPath("$.data.processedCount").value(30))
                .andExpect(jsonPath("$.data.failedCount").value(20))
            
            verify(dlqService).getDLQStats()
        }
        
        @Test
        fun 인증되지_않은_사용자는_접근할_수_없다() {
            // when & then
            mockMvc.perform(get("/api/v1/admin/dlq/stats"))
                .andExpect(status().isUnauthorized)
            
            verify(dlqService, never()).getDLQStats()
        }
        
        @Test
        @WithMockUser(roles = ["USER"])
        fun ADMIN_권한이_없는_사용자는_접근할_수_없다() {
            // when & then
            mockMvc.perform(get("/api/v1/admin/dlq/stats"))
                .andExpect(status().isForbidden)
            
            verify(dlqService, never()).getDLQStats()
        }
    }
    
    @Nested
    @DisplayName("POST /api/v1/admin/dlq/retry/{topic}")
    inner class RetryDLQMessages {
        
        @Test
        @WithMockUser(roles = ["ADMIN"])
        fun 토픽별_DLQ_메시지를_일괄_재처리한다() {
            // given
            val topic = "payment"
            val maxCount = 30
            val expectedResult = mapOf(
                "topic" to topic,
                "totalAttempted" to 10,
                "successCount" to 8,
                "failureCount" to 2,
                "errors" to listOf("ID 1: 재처리 실패", "ID 5: 재처리 실패")
            )
            
            whenever(dlqService.retryDLQMessagesByTopic(topic, maxCount))
                .thenReturn(expectedResult)
            
            // when & then
            mockMvc.perform(
                post("/api/v1/admin/dlq/retry/{topic}", topic)
                    .param("maxCount", maxCount.toString())
                    .with(csrf())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.data.topic").value(topic))
                .andExpect(jsonPath("$.data.totalAttempted").value(10))
                .andExpect(jsonPath("$.data.successCount").value(8))
                .andExpect(jsonPath("$.data.failureCount").value(2))
            
            verify(dlqService).retryDLQMessagesByTopic(topic, maxCount)
        }
        
        @Test
        @WithMockUser(roles = ["ADMIN"])
        fun maxCount_파라미터_없이도_기본값으로_처리한다() {
            // given
            val topic = "payment"
            val expectedResult = mapOf(
                "topic" to topic,
                "totalAttempted" to 5,
                "successCount" to 5,
                "failureCount" to 0,
                "errors" to emptyList<String>()
            )
            
            whenever(dlqService.retryDLQMessagesByTopic(topic, 50)) // 기본값
                .thenReturn(expectedResult)
            
            // when & then
            mockMvc.perform(
                post("/api/v1/admin/dlq/retry/{topic}", topic)
                    .with(csrf())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
            
            verify(dlqService).retryDLQMessagesByTopic(topic, 50)
        }
    }
    
    @Nested
    @DisplayName("POST /api/v1/admin/dlq/retry/message/{id}")
    inner class RetryDLQMessage {
        
        @Test
        @WithMockUser(roles = ["ADMIN"])
        fun 개별_DLQ_메시지를_재처리한다() {
            // given
            val messageId = 123L
            whenever(dlqService.retryDLQMessage(messageId)).thenReturn(true)
            
            // when & then
            mockMvc.perform(
                post("/api/v1/admin/dlq/retry/message/{id}", messageId)
                    .with(csrf())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("DLQ 메시지 재처리 성공: ID 123"))
            
            verify(dlqService).retryDLQMessage(messageId)
        }
        
        @Test
        @WithMockUser(roles = ["ADMIN"])
        fun 재처리_실패_시_실패_메시지를_반환한다() {
            // given
            val messageId = 123L
            whenever(dlqService.retryDLQMessage(messageId)).thenReturn(false)
            
            // when & then
            mockMvc.perform(
                post("/api/v1/admin/dlq/retry/message/{id}", messageId)
                    .with(csrf())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("DLQ 메시지 재처리 실패: ID 123"))
            
            verify(dlqService).retryDLQMessage(messageId)
        }
    }
    
    @Nested
    @DisplayName("DELETE /api/v1/admin/dlq/clear/{topic}")
    inner class ClearProcessedDLQMessages {
        
        @Test
        @WithMockUser(roles = ["ADMIN"])
        fun 처리_완료된_DLQ_메시지를_삭제한다() {
            // given
            val topic = "payment"
            val deletedCount = 25L
            whenever(dlqService.clearProcessedDLQMessages(topic)).thenReturn(deletedCount)
            
            // when & then
            mockMvc.perform(
                delete("/api/v1/admin/dlq/clear/{topic}", topic)
                    .with(csrf())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.data").value("처리 완료된 DLQ 메시지 삭제: payment (삭제된 메시지: 25개)"))
            
            verify(dlqService).clearProcessedDLQMessages(topic)
        }
    }
    
    @Nested
    @DisplayName("GET /api/v1/admin/dlq/messages/{topic}")
    inner class GetDLQMessages {
        
        @Test
        @WithMockUser(roles = ["ADMIN"])
        fun 토픽별_DLQ_메시지를_페이징하여_조회한다() {
            // given
            val topic = "payment"
            val page = 1
            val size = 10
            val dlqMessages = listOf(createDLQMessage(topic = topic))
            val expectedPage = PageImpl(dlqMessages, PageRequest.of(page, size), 1)
            
            whenever(dlqService.getDLQMessages(topic, page, size)).thenReturn(expectedPage)
            
            // when & then
            mockMvc.perform(
                get("/api/v1/admin/dlq/messages/{topic}", topic)
                    .param("page", page.toString())
                    .param("size", size.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray)
                .andExpect(jsonPath("$.data.totalElements").value(1))
            
            verify(dlqService).getDLQMessages(topic, page, size)
        }
        
        @Test
        @WithMockUser(roles = ["ADMIN"])
        fun 페이징_파라미터_없이도_기본값으로_조회한다() {
            // given
            val topic = "payment"
            val dlqMessages = listOf(createDLQMessage(topic = topic))
            val expectedPage = PageImpl(dlqMessages, PageRequest.of(0, 20), 1)
            
            whenever(dlqService.getDLQMessages(topic, 0, 20)).thenReturn(expectedPage)
            
            // when & then
            mockMvc.perform(get("/api/v1/admin/dlq/messages/{topic}", topic))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
            
            verify(dlqService).getDLQMessages(topic, 0, 20)
        }
    }
    
    @Nested
    @DisplayName("GET /api/v1/admin/dlq/messages")
    inner class GetDLQMessagesByStatus {
        
        @Test
        @WithMockUser(roles = ["ADMIN"])
        fun 상태별_DLQ_메시지를_페이징하여_조회한다() {
            // given
            val status = DLQStatus.PENDING
            val page = 0
            val size = 20
            val dlqMessages = listOf(createDLQMessage(status = status))
            val expectedPage = PageImpl(dlqMessages, PageRequest.of(page, size), 1)
            
            whenever(dlqService.getDLQMessagesByStatus(status, page, size))
                .thenReturn(expectedPage)
            
            // when & then
            mockMvc.perform(
                get("/api/v1/admin/dlq/messages")
                    .param("status", status.name)
                    .param("page", page.toString())
                    .param("size", size.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray)
            
            verify(dlqService).getDLQMessagesByStatus(status, page, size)
        }
    }
    
    private fun createDLQMessage(
        topic: String = "test-topic",
        partition: Int = 0,
        offset: Long = 1000L,
        key: String? = "test-key",
        value: String? = "test-value",
        exception: String? = "Test exception",
        timestamp: Long = System.currentTimeMillis(),
        status: DLQStatus = DLQStatus.PENDING
    ): DLQMessage {
        return DLQMessage(
            originalTopic = topic,
            originalPartition = partition,
            originalOffset = offset,
            originalKey = key,
            originalValue = value,
            exceptionMessage = exception,
            errorTimestamp = timestamp
        ).apply {
            this.status = status
        }
    }
}