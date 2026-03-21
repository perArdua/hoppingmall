package com.hoppingmall.notification.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.notification.domain.Notification
import com.hoppingmall.notification.domain.NotificationRepository
import com.hoppingmall.notification.dto.response.NotificationResponse
import com.hoppingmall.notification.enums.NotificationType
import com.hoppingmall.notification.service.NotificationSseService
import com.hoppingmall.notification.service.SseNotificationMessage
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.redis.core.RedisTemplate
import com.hoppingmall.common.KafkaTopics
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class NotificationEventConsumer(
    private val notificationRepository: NotificationRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val cacheManager: CacheManager
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [KafkaTopics.NOTIFICATION], groupId = "notification-service")
    fun handleNotificationEvent(
        @Payload event: Map<String, Any>
    ) {
        val eventId = event["event_id"]?.toString() ?: event["eventId"]?.toString() ?: return
        val userId = (event["user_id"] ?: event["userId"])?.toString()?.toLongOrNull() ?: return
        val typeStr = event["type"]?.toString() ?: return
        val title = event["title"]?.toString() ?: return
        val content = event["content"]?.toString() ?: return
        val metadata = event["metadata"]?.toString()

        try {
            if (notificationRepository.existsByEventId(eventId)) {
                log.info("이미 처리된 알림 이벤트: eventId={}", eventId)
                return
            }

            val type = try {
                NotificationType.valueOf(typeStr)
            } catch (e: IllegalArgumentException) {
                log.warn("알 수 없는 알림 타입: type={}", typeStr)
                return
            }

            val notification = Notification(
                eventId = eventId,
                userId = userId,
                type = type,
                title = title,
                content = content,
                metadata = metadata
            )

            val saved: Notification
            try {
                saved = notificationRepository.save(notification)
            } catch (e: DataIntegrityViolationException) {
                log.info("이미 처리된 알림 이벤트: eventId={}", eventId)
                return
            }

            cacheManager.getCache("unread-count")?.evict(userId)
            publishSseEvent(saved)
            log.info("알림 처리 완료: userId={}, type={}, title={}", userId, type, title)
        } catch (e: Exception) {
            log.error("알림 처리 실패: eventId={}, userId={}, 오류={}", eventId, userId, e.message)
            throw e
        }
    }

    private fun publishSseEvent(notification: Notification) {
        try {
            val message = SseNotificationMessage(
                userId = notification.userId,
                notification = NotificationResponse.from(notification)
            )
            redisTemplate.convertAndSend(
                NotificationSseService.CHANNEL_NAME,
                objectMapper.writeValueAsString(message)
            )
        } catch (e: Exception) {
            log.warn("SSE Redis publish 실패: userId={}, error={}", notification.userId, e.message)
        }
    }
}
