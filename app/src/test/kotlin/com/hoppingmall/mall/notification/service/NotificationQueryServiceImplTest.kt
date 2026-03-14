package com.hoppingmall.mall.notification.service

import com.hoppingmall.mall.notification.domain.Notification
import com.hoppingmall.mall.notification.domain.NotificationRepository
import com.hoppingmall.mall.notification.enum.NotificationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort

@DisplayName("NotificationQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationQueryServiceImplTest {

    private val notificationRepository: NotificationRepository = mock()
    private val notificationQueryService = NotificationQueryServiceImpl(notificationRepository)

    private fun createNotification(
        id: Long = 1L,
        userId: Long = 1L,
        type: NotificationType = NotificationType.PAYMENT_COMPLETED,
        isRead: Boolean = false
    ): Notification {
        val notification = Notification(
            eventId = "event-$id",
            userId = userId,
            type = type,
            title = "테스트 알림 $id",
            content = "테스트 내용 $id",
            isRead = isRead
        )
        val idField = notification.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(notification, id)
        return notification
    }

    @Nested
    @DisplayName("getNotifications")
    inner class GetNotifications {

        @Test
        fun 알림_목록을_페이지네이션으로_조회한다() {
            val userId = 1L
            val pageable = PageRequest.of(0, 20, Sort.by("createdAt"))
            val notifications = listOf(
                createNotification(id = 1L, userId = userId),
                createNotification(id = 2L, userId = userId)
            )

            whenever(notificationRepository.findByUserId(userId, pageable))
                .thenReturn(SliceImpl(notifications, pageable, false))

            val result = notificationQueryService.getNotifications(userId, null, pageable)

            assertEquals(2, result.content.size)
            assertEquals(1L, result.content[0].id)
            assertEquals(2L, result.content[1].id)
        }

        @Test
        fun 타입_필터로_알림을_조회한다() {
            val userId = 1L
            val type = NotificationType.POINT_EARNED
            val pageable = PageRequest.of(0, 20, Sort.by("createdAt"))
            val notifications = listOf(
                createNotification(id = 3L, userId = userId, type = type)
            )

            whenever(notificationRepository.findByUserIdAndType(userId, type, pageable))
                .thenReturn(SliceImpl(notifications, pageable, false))

            val result = notificationQueryService.getNotifications(userId, type, pageable)

            assertEquals(1, result.content.size)
            assertEquals(NotificationType.POINT_EARNED, result.content[0].type)
        }
    }

    @Nested
    @DisplayName("getUnreadCount")
    inner class GetUnreadCount {

        @Test
        fun 안읽은_알림_수를_조회한다() {
            val userId = 1L

            whenever(notificationRepository.countByUserIdAndIsRead(userId, false))
                .thenReturn(5L)

            val result = notificationQueryService.getUnreadCount(userId)

            assertEquals(5L, result.count)
        }

        @Test
        fun 안읽은_알림이_없으면_0을_반환한다() {
            val userId = 1L

            whenever(notificationRepository.countByUserIdAndIsRead(userId, false))
                .thenReturn(0L)

            val result = notificationQueryService.getUnreadCount(userId)

            assertEquals(0L, result.count)
        }
    }
}
