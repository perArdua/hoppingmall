package com.hoppingmall.notification.dto.response

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
    val createdAt: LocalDateTime,
    val orderId: Long? = null,
    val refundId: Long? = null
) {
    companion object {
        private val metadataReader = ObjectMapper()
        private val orderIdKeys = listOf("orderId", "order_id")
        private val refundIdKeys = listOf("refundId", "refund_id")

        fun from(notification: Notification): NotificationResponse {
            return NotificationResponse(
                id = notification.id!!,
                type = notification.type,
                title = notification.title,
                content = notification.content,
                metadata = notification.metadata,
                isRead = notification.isRead,
                createdAt = notification.createdAt,
                orderId = idOf(notification.metadata, orderIdKeys),
                refundId = idOf(notification.metadata, refundIdKeys)
            )
        }

        private fun idOf(metadata: String?, keys: List<String>): Long? {
            if (metadata.isNullOrBlank()) return null
            val root = runCatching { metadataReader.readTree(metadata) }.getOrNull()
            if (root == null || !root.isObject) return null
            return keys.firstNotNullOfOrNull { root.get(it) }?.let(::positiveLong)
        }

        private fun positiveLong(node: JsonNode): Long? {
            val value = when {
                node.isIntegralNumber && node.canConvertToLong() -> node.longValue()
                node.isTextual -> node.textValue().toLongOrNull()
                else -> null
            }
            return value?.takeIf { it > 0 }
        }
    }
}
