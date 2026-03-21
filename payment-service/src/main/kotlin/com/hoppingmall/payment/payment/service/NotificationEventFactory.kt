package com.hoppingmall.payment.payment.service

import com.hoppingmall.payment.common.NotificationType
import org.springframework.stereotype.Component

@Component
class NotificationEventFactory {

    fun createNotificationEventData(
        eventId: String,
        userId: Long,
        type: NotificationType,
        title: String,
        content: String,
        metadata: String
    ): Map<String, Any> = mapOf(
        "eventId" to eventId,
        "userId" to userId,
        "type" to type.toString(),
        "title" to title,
        "content" to content,
        "metadata" to metadata
    )
}
