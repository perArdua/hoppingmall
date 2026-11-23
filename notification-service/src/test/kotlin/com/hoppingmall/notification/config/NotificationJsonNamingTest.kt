package com.hoppingmall.notification.config

import com.hoppingmall.dlq.controller.DLQController
import com.hoppingmall.dlq.service.DLQCommandService
import com.hoppingmall.dlq.service.DLQQueryService
import com.hoppingmall.notification.controller.NotificationController
import com.hoppingmall.notification.dto.response.NotificationResponse
import com.hoppingmall.notification.enums.NotificationType
import com.hoppingmall.notification.service.NotificationCommandService
import com.hoppingmall.notification.service.NotificationQueryService
import com.hoppingmall.notification.service.NotificationSseService
import io.micrometer.tracing.Tracer
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

@DisplayName("알림 응답 JSON 이름")
@DisplayNameGeneration(ReplaceUnderscores::class)
@WebMvcTest(controllers = [DLQController::class, NotificationController::class])
@Import(SecurityConfig::class)
class NotificationJsonNamingTest {

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
    fun 알림_목록은_다른_서비스처럼_snake_case_이름으로_나간다() {
        val notification = NotificationResponse(
            id = 1L,
            type = NotificationType.PAYMENT_COMPLETED,
            title = "결제 완료",
            content = "결제가 끝났습니다",
            metadata = null,
            isRead = false,
            createdAt = LocalDateTime.of(2026, 11, 16, 9, 0)
        )
        whenever(notificationQueryService.getNotifications(eq(7L), anyOrNull(), any()))
            .thenReturn(SliceImpl(listOf(notification), PageRequest.of(0, 20), false))

        mockMvc.get("/api/v1/notifications") {
            header("X-User-Id", "7")
            header("x-user-role", "BUYER")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].is_read") { value(false) }
            jsonPath("$.data.content[0].created_at") { exists() }
            jsonPath("$.data.content[0].isRead") { doesNotExist() }
            jsonPath("$.data.number_of_elements") { value(1) }
            jsonPath("$.data.pageable.page_size") { value(20) }
        }
    }
}
