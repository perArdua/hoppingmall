package com.hoppingmall.notification.dto.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.hoppingmall.notification.enums.NotificationType

@JsonIgnoreProperties(ignoreUnknown = true)
data class NotificationEvent(
    val eventId: String = "",
    val userId: Long = 0,
    val type: NotificationType = NotificationType.PAYMENT_COMPLETED,
    val title: String = "",
    val content: String = "",
    val metadata: String? = null
)
