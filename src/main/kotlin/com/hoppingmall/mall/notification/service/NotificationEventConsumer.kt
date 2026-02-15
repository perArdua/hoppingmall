package com.hoppingmall.mall.notification.service

import com.hoppingmall.mall.notification.domain.Notification
import com.hoppingmall.mall.notification.domain.NotificationRepository
import com.hoppingmall.mall.notification.dto.event.NotificationEvent
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
    
    @KafkaListener(topics = ["notification"], groupId = "notification-service")
    fun handleNotificationEvent(
        @Payload event: NotificationEvent
    ) {
        // Key 기반 파티셔닝으로 같은 userId의 알림은 같은 파티션에서 순차 처리
        println("알림 처리: userId=${event.userId}, type=${event.type}")

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
        
        println("알림 저장 완료: 사용자 ${event.userId}, 타입 ${event.type}, 제목 ${event.title}")
    }
} 
