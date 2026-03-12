package com.hoppingmall.mall.notification.dto.response

import com.hoppingmall.mall.notification.domain.Notification
import com.hoppingmall.mall.notification.enum.NotificationType
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
