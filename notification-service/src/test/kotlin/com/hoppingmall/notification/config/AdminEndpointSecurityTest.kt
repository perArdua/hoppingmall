package com.hoppingmall.notification.config

import com.hoppingmall.dlq.controller.DLQController
import com.hoppingmall.dlq.service.DLQCommandService
import com.hoppingmall.dlq.service.DLQQueryService
import com.hoppingmall.notification.controller.NotificationController
import com.hoppingmall.notification.dto.response.UnreadCountResponse
import com.hoppingmall.notification.service.NotificationCommandService
import com.hoppingmall.notification.service.NotificationQueryService
import com.hoppingmall.notification.service.NotificationSseService
import io.micrometer.tracing.Tracer
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@DisplayName("알림 서비스 관리자 API 권한")
@DisplayNameGeneration(ReplaceUnderscores::class)
@WebMvcTest(controllers = [DLQController::class, NotificationController::class])
@Import(SecurityConfig::class)
class AdminEndpointSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var dlqCommandService: DLQCommandService

    @MockitoBean
    private lateinit var dlqQueryService: DLQQueryService

    @MockitoBean
    private lateinit var notificationQueryService: NotificationQueryService

    @MockitoBean
    private lateinit var notificationCommandService: NotificationCommandService

    @MockitoBean
    private lateinit var notificationSseService: NotificationSseService

    @MockitoBean
    private lateinit var tracer: Tracer

    @Test
    fun BUYER가_DLQ_통계를_조회하면_403() {
        mockMvc.get("/api/v1/admin/dlq/stats") {
            header("X-User-Id", "1")
            header("x-user-role", "BUYER")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun SELLER가_DLQ_메시지를_재처리하면_403() {
        mockMvc.post("/api/v1/admin/dlq/retry/message/1") {
            header("X-User-Id", "1")
            header("x-user-role", "SELLER")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun BUYER가_토픽의_DLQ_메시지를_재처리하면_403() {
        mockMvc.post("/api/v1/admin/dlq/retry/notification") {
            header("X-User-Id", "1")
            header("x-user-role", "BUYER")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun BUYER가_처리된_DLQ_메시지를_지우면_403() {
        mockMvc.delete("/api/v1/admin/dlq/clear/notification") {
            header("X-User-Id", "1")
            header("x-user-role", "BUYER")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun 역할_헤더_없이_DLQ_메시지를_조회하면_403() {
        mockMvc.get("/api/v1/admin/dlq/messages/notification") {
            header("X-User-Id", "1")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun 인증_없이_DLQ_통계를_조회하면_403() {
        mockMvc.get("/api/v1/admin/dlq/stats")
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun ADMIN은_DLQ_통계를_조회한다() {
        whenever(dlqQueryService.getDLQStats()).thenReturn(mapOf("total" to 0))

        mockMvc.get("/api/v1/admin/dlq/stats") {
            header("X-User-Id", "1")
            header("x-user-role", "ADMIN")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun BUYER는_자기_알림의_안_읽은_수를_그대로_조회한다() {
        whenever(notificationQueryService.getUnreadCount(1L)).thenReturn(UnreadCountResponse(0L))

        mockMvc.get("/api/v1/notifications/unread-count") {
            header("X-User-Id", "1")
            header("x-user-role", "BUYER")
        }.andExpect { status { isOk() } }
    }
}
