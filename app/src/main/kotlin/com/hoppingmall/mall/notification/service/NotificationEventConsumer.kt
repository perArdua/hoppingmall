package com.hoppingmall.mall.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.notification.domain.Notification
import com.hoppingmall.mall.notification.domain.NotificationRepository
import com.hoppingmall.mall.notification.dto.event.NotificationEvent
import com.hoppingmall.mall.notification.dto.response.NotificationResponse
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class NotificationEventConsumer(
    private val notificationRepository: NotificationRepository,
    @Qualifier("customStringRedisTemplate") private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["notification"], groupId = "notification-service")
    fun handleNotificationEvent(
        @Payload event: NotificationEvent
    ) {
        try {
            if (notificationRepository.existsByEventId(event.eventId)) {
                log.info("이미 처리된 알림 이벤트: eventId={}", event.eventId)
                return
            }

            val notification = Notification(
                eventId = event.eventId,
                userId = event.userId,
                type = event.type,
                title = event.title,
                content = event.content,
                metadata = event.metadata
            )

            val saved: Notification
            try {
                saved = notificationRepository.save(notification)
            } catch (e: DataIntegrityViolationException) {
                log.info("이미 처리된 알림 이벤트: eventId={}", event.eventId)
                return
            }

            publishSseEvent(saved)
            log.info("알림 처리 완료: userId={}, type={}, title={}", event.userId, event.type, event.title)
        } catch (e: Exception) {
            log.error("알림 처리 실패: eventId={}, userId={}, 오류={}", event.eventId, event.userId, e.message)
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
