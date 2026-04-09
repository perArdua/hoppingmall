package com.hoppingmall.notification.controller

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.notification.dto.response.NotificationResponse
import com.hoppingmall.notification.dto.response.UnreadCountResponse
import com.hoppingmall.notification.enums.NotificationType
import com.hoppingmall.notification.service.NotificationCommandService
import com.hoppingmall.notification.service.NotificationQueryService
import com.hoppingmall.notification.service.NotificationSseService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@DisplayName("NotificationController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationControllerTest {

    @Mock
    private lateinit var notificationQueryService: NotificationQueryService

    @Mock
    private lateinit var notificationCommandService: NotificationCommandService

    @Mock
    private lateinit var notificationSseService: NotificationSseService

    private lateinit var notificationController: NotificationController

    private lateinit var userPrincipal: UserPrincipal

    @BeforeEach
    fun setUp() {
        notificationController = NotificationController(
            notificationQueryService,
            notificationCommandService,
            notificationSseService
        )
        userPrincipal = UserPrincipal.of(1L, "BUYER")
    }

    @Test
    fun SSE_구독을_요청한다() {
        val emitter = SseEmitter()
        whenever(notificationSseService.connect(1L)).thenReturn(emitter)

        val result = notificationController.subscribe(userPrincipal)

        assertThat(result).isSameAs(emitter)
    }

    @Test
    fun 알림_목록을_조회한다() {
        val pageable = PageRequest.of(0, 20)
        val response = NotificationResponse(
            id = 1L, type = NotificationType.PAYMENT_COMPLETED, title = "결제",
            content = "내용", metadata = null, isRead = false, createdAt = LocalDateTime.now()
        )
        whenever(notificationQueryService.getNotifications(1L, null, pageable))
            .thenReturn(SliceImpl(listOf(response), pageable, false))

        val result = notificationController.getNotifications(userPrincipal, null, pageable)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data!!.content).hasSize(1)
    }

    @Test
    fun 타입별_알림_목록을_조회한다() {
        val pageable = PageRequest.of(0, 20)
        val type = NotificationType.PAYMENT_COMPLETED
        whenever(notificationQueryService.getNotifications(1L, type, pageable))
            .thenReturn(SliceImpl(emptyList(), pageable, false))

        val result = notificationController.getNotifications(userPrincipal, type, pageable)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 읽지_않은_알림_수를_조회한다() {
        whenever(notificationQueryService.getUnreadCount(1L))
            .thenReturn(UnreadCountResponse(3L))

        val result = notificationController.getUnreadCount(userPrincipal)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(result.body!!.data!!.count).isEqualTo(3L)
    }

    @Test
    fun 알림을_읽음_처리한다() {
        val result = notificationController.markAsRead(userPrincipal, 1L)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        verify(notificationCommandService).markAsRead(1L, 1L)
    }

    @Test
    fun 모든_알림을_읽음_처리한다() {
        val result = notificationController.markAllAsRead(userPrincipal)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        verify(notificationCommandService).markAllAsRead(1L)
    }
}
