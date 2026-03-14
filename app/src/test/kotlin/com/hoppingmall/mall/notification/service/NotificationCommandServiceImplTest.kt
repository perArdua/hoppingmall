package com.hoppingmall.mall.notification.service

import com.hoppingmall.mall.notification.domain.Notification
import com.hoppingmall.mall.notification.domain.NotificationRepository
import com.hoppingmall.mall.notification.enum.NotificationType
import com.hoppingmall.mall.notification.exception.NotificationAccessDeniedException
import com.hoppingmall.mall.notification.exception.NotificationNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

@DisplayName("NotificationCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationCommandServiceImplTest {

    private val notificationRepository: NotificationRepository = mock()
    private val notificationCommandService = NotificationCommandServiceImpl(notificationRepository)

    private fun createNotification(
        id: Long = 1L,
        userId: Long = 1L,
        isRead: Boolean = false
    ): Notification {
        val notification = Notification(
            eventId = "event-$id",
            userId = userId,
            type = NotificationType.PAYMENT_COMPLETED,
            title = "테스트 알림",
            content = "테스트 내용",
            isRead = isRead
        )
        val idField = notification.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(notification, id)
        return notification
    }

    @Nested
    @DisplayName("markAsRead")
    inner class MarkAsRead {

        @Test
        fun 알림을_읽음_처리한다() {
            val userId = 1L
            val notificationId = 1L
            val notification = createNotification(id = notificationId, userId = userId)

            whenever(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(notification))

            notificationCommandService.markAsRead(notificationId, userId)

            assertTrue(notification.isRead)
        }

        @Test
        fun 존재하지_않는_알림이면_예외가_발생한다() {
            val notificationId = 999L

            whenever(notificationRepository.findById(notificationId))
                .thenReturn(Optional.empty())

            assertThrows<NotificationNotFoundException> {
                notificationCommandService.markAsRead(notificationId, 1L)
            }
        }

        @Test
        fun 타인의_알림이면_예외가_발생한다() {
            val ownerId = 1L
            val otherUserId = 2L
            val notificationId = 1L
            val notification = createNotification(id = notificationId, userId = ownerId)

            whenever(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(notification))

            assertThrows<NotificationAccessDeniedException> {
                notificationCommandService.markAsRead(notificationId, otherUserId)
            }
        }

        @Test
        fun 이미_읽은_알림도_정상_처리된다() {
            val userId = 1L
            val notificationId = 1L
            val notification = createNotification(id = notificationId, userId = userId, isRead = true)

            whenever(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(notification))

            notificationCommandService.markAsRead(notificationId, userId)

            assertTrue(notification.isRead)
        }
    }

    @Nested
    @DisplayName("markAllAsRead")
    inner class MarkAllAsRead {

        @Test
        fun 전체_알림을_읽음_처리한다() {
            val userId = 1L

            whenever(notificationRepository.markAllAsRead(userId)).thenReturn(3)

            notificationCommandService.markAllAsRead(userId)

            verify(notificationRepository).markAllAsRead(userId)
        }
    }
}
