package com.hoppingmall.mall.notification.service

import com.hoppingmall.mall.notification.domain.Notification
import com.hoppingmall.mall.notification.domain.NotificationRepository
import com.hoppingmall.mall.notification.dto.event.NotificationEvent
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class NotificationEventConsumer(
    private val notificationRepository: NotificationRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["notification"], groupId = "notification-service")
    fun handleNotificationEvent(
        @Payload event: NotificationEvent
    ) {
        log.info("알림 처리: userId={}, type={}", event.userId, event.type)

        if (notificationRepository.existsByEventId(event.eventId)) {
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

        try {
            notificationRepository.save(notification)
        } catch (e: DataIntegrityViolationException) {
            return
        }

        log.info("알림 저장 완료: userId={}, type={}, title={}", event.userId, event.type, event.title)
    }
} 
