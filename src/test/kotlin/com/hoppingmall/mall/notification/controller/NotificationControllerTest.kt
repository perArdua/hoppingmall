package com.hoppingmall.mall.notification.controller

import com.hoppingmall.mall.global.auth.UserPrincipal
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.notification.dto.response.NotificationResponse
import com.hoppingmall.mall.notification.dto.response.UnreadCountResponse
import com.hoppingmall.mall.notification.enum.NotificationType
import com.hoppingmall.mall.notification.service.NotificationCommandService
import com.hoppingmall.mall.notification.service.NotificationQueryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

@DisplayName("NotificationController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationControllerTest {

    private val notificationQueryService: NotificationQueryService = mock()
    private val notificationCommandService: NotificationCommandService = mock()
    private val controller = NotificationController(notificationQueryService, notificationCommandService)

    private val userPrincipal = UserPrincipal(1L, "test@example.com", "BUYER")
    private val now = LocalDateTime.now()

    private fun createNotificationResponse(
        id: Long = 1L,
        type: NotificationType = NotificationType.PAYMENT_COMPLETED
    ): NotificationResponse {
        return NotificationResponse(
            id = id,
            type = type,
            title = "테스트 알림",
            content = "테스트 내용",
            metadata = null,
            isRead = false,
            createdAt = now
        )
    }

    @Nested
    @DisplayName("getNotifications")
    inner class GetNotifications {

        @Test
        fun 알림_목록을_조회한다() {
            val pageable = PageRequest.of(0, 20, Sort.by("createdAt"))
            val notifications = listOf(createNotificationResponse(id = 1L), createNotificationResponse(id = 2L))
            val slice: Slice<NotificationResponse> = SliceImpl(notifications, pageable, false)

            whenever(notificationQueryService.getNotifications(1L, null, pageable))
                .thenReturn(slice)

            val response = controller.getNotifications(userPrincipal, null, pageable)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("SUCCESS", response.body?.code)
            assertEquals(2, response.body?.data?.content?.size)
        }

        @Test
        fun 타입_필터로_조회한다() {
            val type = NotificationType.POINT_EARNED
            val pageable = PageRequest.of(0, 20, Sort.by("createdAt"))
            val notifications = listOf(createNotificationResponse(id = 1L, type = type))
            val slice: Slice<NotificationResponse> = SliceImpl(notifications, pageable, false)

            whenever(notificationQueryService.getNotifications(1L, type, pageable))
                .thenReturn(slice)

            val response = controller.getNotifications(userPrincipal, type, pageable)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(1, response.body?.data?.content?.size)
        }
    }

    @Nested
    @DisplayName("getUnreadCount")
    inner class GetUnreadCount {

        @Test
        fun 안읽은_알림_수를_조회한다() {
            whenever(notificationQueryService.getUnreadCount(1L))
                .thenReturn(UnreadCountResponse(3L))

            val response = controller.getUnreadCount(userPrincipal)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(3L, response.body?.data?.count)
        }
    }

    @Nested
    @DisplayName("markAsRead")
    inner class MarkAsRead {

        @Test
        fun 알림을_읽음_처리한다() {
            val response = controller.markAsRead(userPrincipal, 1L)

            assertEquals(HttpStatus.OK, response.statusCode)
            verify(notificationCommandService).markAsRead(1L, 1L)
        }
    }

    @Nested
    @DisplayName("markAllAsRead")
    inner class MarkAllAsRead {

        @Test
        fun 전체_알림을_읽음_처리한다() {
            val response = controller.markAllAsRead(userPrincipal)

            assertEquals(HttpStatus.OK, response.statusCode)
            verify(notificationCommandService).markAllAsRead(1L)
        }
    }
}
