package com.hoppingmall.notification.dto.response

import com.hoppingmall.notification.domain.Notification
import com.hoppingmall.notification.enums.NotificationType
import java.time.LocalDateTime

data class NotificationResponse(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val content: String,
    val metadata: String?,
    val isRead: Boolean,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(notification: Notification): NotificationResponse {
            return NotificationResponse(
                id = notification.id!!,
                type = notification.type,
                title = notification.title,
                content = notification.content,
                metadata = notification.metadata,
                isRead = notification.isRead,
                createdAt = notification.createdAt
            )
        }
    }
}
