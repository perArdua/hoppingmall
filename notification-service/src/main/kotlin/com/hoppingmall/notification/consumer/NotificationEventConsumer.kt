package com.hoppingmall.notification.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.common.consumer.executeIdempotently
import com.hoppingmall.notification.domain.Notification
import com.hoppingmall.notification.domain.NotificationRepository
import com.hoppingmall.notification.dto.response.NotificationResponse
import com.hoppingmall.notification.enums.NotificationType
import com.hoppingmall.notification.service.NotificationSseService
import com.hoppingmall.notification.service.SseNotificationMessage
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.data.redis.core.RedisTemplate
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
@Transactional
class NotificationEventConsumer(
    private val notificationRepository: NotificationRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val cacheManager: CacheManager,
    private val meterRegistry: MeterRegistry?
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [KafkaTopics.NOTIFICATION], groupId = "notification-service")
    @Suppress("UNCHECKED_CAST")
    fun handleNotificationEvent(
        record: ConsumerRecord<String, Any>
    ) {
        val value = record.value()
        val event: Map<String, Any> = when (value) {
            is Map<*, *> -> value as Map<String, Any>
            is String -> objectMapper.readValue(value, Map::class.java) as Map<String, Any>
            else -> objectMapper.readValue(value.toString(), Map::class.java) as Map<String, Any>
        }

        val eventId = event["event_id"]?.toString() ?: event["eventId"]?.toString()
            ?: return discard(record, null, "missing_event_id")
        val userId = (event["user_id"] ?: event["userId"])?.toString()?.toLongOrNull()?.takeIf { it > 0 }
            ?: return discard(record, eventId, "invalid_user_id")
        val typeStr = event["type"]?.toString() ?: return discard(record, eventId, "missing_type")
        val title = event["title"]?.toString() ?: return discard(record, eventId, "missing_title")
        val content = event["content"]?.toString() ?: return discard(record, eventId, "missing_content")
        val metadata = event["metadata"]?.toString()

        executeIdempotently(
            eventId = eventId,
            eventDescription = "알림",
            logger = log,
            existsCheck = { notificationRepository.existsByEventId(eventId) }
        ) {
            val type = try {
                NotificationType.valueOf(typeStr)
            } catch (e: IllegalArgumentException) {
                log.warn("알 수 없는 알림 타입: type={}", typeStr)
                return@executeIdempotently
            }

            val saved = notificationRepository.save(
                Notification(
                    eventId = eventId,
                    userId = userId,
                    type = type,
                    title = title,
                    content = content,
                    metadata = metadata
                )
            )

            evictUnreadCountAndPublishAfterCommit(saved)
            log.info("알림 처리 완료: userId={}, type={}, title={}", userId, type, title)
        }
    }

    private fun discard(record: ConsumerRecord<String, Any>, eventId: String?, reason: String) {
        log.warn(
            "알림 이벤트 버림: reason={}, eventId={}, topic={}, partition={}, offset={}",
            reason, eventId, record.topic(), record.partition(), record.offset()
        )
        meterRegistry?.counter(DISCARDED_METRIC, "reason", reason)?.increment()
    }

    private fun evictUnreadCountAndPublishAfterCommit(notification: Notification) {
        val cache = cacheManager.getCache("unread-count")
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cache?.evict(notification.userId)
            publishSseEvent(notification)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                cache?.evict(notification.userId)
                publishSseEvent(notification)
            }
        })
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

    companion object {
        const val DISCARDED_METRIC = "notification.event.discarded"
    }
}
