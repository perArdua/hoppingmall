package com.hoppingmall.notification.support.fixture

import com.hoppingmall.notification.domain.Notification
import com.hoppingmall.notification.enums.NotificationType

fun createNotification(
    eventId: String = "event-123",
    userId: Long = 1L,
    type: NotificationType = NotificationType.PAYMENT_COMPLETED,
    title: String = "테스트 알림",
    content: String = "테스트 알림 내용",
    metadata: String? = null,
    isRead: Boolean = false
): Notification = Notification(
    eventId = eventId,
    userId = userId,
    type = type,
    title = title,
    content = content,
    metadata = metadata,
    isRead = isRead
)
