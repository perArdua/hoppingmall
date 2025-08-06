package com.hoppingmall.mall.notification.dto.event

import com.hoppingmall.mall.notification.enum.NotificationType

data class NotificationEvent(
    val userId: Long,
    val type: NotificationType,
    val title: String,
    val content: String,
    val metadata: String? = null
) 