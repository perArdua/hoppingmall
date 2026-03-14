package com.hoppingmall.notification.dto.event

import com.hoppingmall.notification.enums.NotificationType

data class NotificationEvent(
    val eventId: String,
    val userId: Long,
    val type: NotificationType,
    val title: String,
    val content: String,
    val metadata: String? = null
)
