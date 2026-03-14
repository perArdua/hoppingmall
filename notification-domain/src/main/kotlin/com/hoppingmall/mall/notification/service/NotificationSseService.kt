package com.hoppingmall.mall.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.notification.dto.response.NotificationResponse
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException

@Service
class NotificationSseService(
    private val sseEmitterRepository: SseEmitterRepository,
    private val redisMessageListenerContainer: RedisMessageListenerContainer,
    private val objectMapper: ObjectMapper
) : MessageListener {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val CHANNEL_NAME = "notification:sse"
        const val SSE_TIMEOUT = 1800000L
    }

    @PostConstruct
    fun subscribe() {
        redisMessageListenerContainer.addMessageListener(this, ChannelTopic(CHANNEL_NAME))
    }

    @PreDestroy
    fun unsubscribe() {
        redisMessageListenerContainer.removeMessageListener(this)
    }

    fun connect(userId: Long): SseEmitter {
        val emitter = SseEmitter(SSE_TIMEOUT)

        sseEmitterRepository.save(userId, emitter)

        emitter.onCompletion { sseEmitterRepository.remove(userId, emitter) }
        emitter.onTimeout { sseEmitterRepository.remove(userId, emitter) }
        emitter.onError { sseEmitterRepository.remove(userId, emitter) }

        try {
            emitter.send(SseEmitter.event().name("connect").data("connected"))
        } catch (e: IOException) {
            sseEmitterRepository.remove(userId, emitter)
        }

        return emitter
    }

    fun sendToUser(userId: Long, response: NotificationResponse) {
        val emitters = sseEmitterRepository.findByUserId(userId)
        emitters.forEach { emitter ->
            try {
                emitter.send(SseEmitter.event().name("notification").data(response))
            } catch (e: IOException) {
                sseEmitterRepository.remove(userId, emitter)
            }
        }
    }

    override fun onMessage(message: Message, pattern: ByteArray?) {
        try {
            val sseMessage = objectMapper.readValue(message.body, SseNotificationMessage::class.java)
            sendToUser(sseMessage.userId, sseMessage.notification)
        } catch (e: Exception) {
            log.error("Redis SSE 메시지 처리 실패: {}", e.message)
        }
    }
}

data class SseNotificationMessage(
    val userId: Long,
    val notification: NotificationResponse
)
