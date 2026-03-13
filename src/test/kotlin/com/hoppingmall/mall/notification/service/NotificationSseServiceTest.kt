package com.hoppingmall.mall.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.hoppingmall.mall.notification.dto.response.NotificationResponse
import com.hoppingmall.mall.notification.enum.NotificationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime

@DisplayName("NotificationSseService")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationSseServiceTest {

    private val sseEmitterRepository = SseEmitterRepository()
    private val redisMessageListenerContainer: RedisMessageListenerContainer = mock()
    private val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
    }
    private val notificationSseService = NotificationSseService(
        sseEmitterRepository, redisMessageListenerContainer, objectMapper
    )

    private val now = LocalDateTime.now()

    private fun createNotificationResponse(id: Long = 1L): NotificationResponse {
        return NotificationResponse(
            id = id,
            type = NotificationType.PAYMENT_COMPLETED,
            title = "테스트 알림",
            content = "테스트 내용",
            metadata = null,
            isRead = false,
            createdAt = now
        )
    }

    @Nested
    @DisplayName("connect")
    inner class Connect {

        @Test
        fun SSE_연결을_생성한다() {
            val emitter = notificationSseService.connect(1L)

            assertNotNull(emitter)
            assertEquals(1, sseEmitterRepository.findByUserId(1L).size)
        }

        @Test
        fun 같은_사용자가_여러_연결을_생성한다() {
            notificationSseService.connect(1L)
            notificationSseService.connect(1L)

            assertEquals(2, sseEmitterRepository.findByUserId(1L).size)
        }
    }

    @Nested
    @DisplayName("subscribe")
    inner class Subscribe {

        @Test
        fun Redis_채널을_구독한다() {
            notificationSseService.subscribe()

            verify(redisMessageListenerContainer).addMessageListener(
                org.mockito.kotlin.eq(notificationSseService),
                org.mockito.kotlin.eq(org.springframework.data.redis.listener.ChannelTopic(NotificationSseService.CHANNEL_NAME))
            )
        }
    }

    @Nested
    @DisplayName("unsubscribe")
    inner class Unsubscribe {

        @Test
        fun Redis_채널_구독을_해제한다() {
            notificationSseService.unsubscribe()

            verify(redisMessageListenerContainer).removeMessageListener(notificationSseService)
        }
    }

    @Nested
    @DisplayName("sendToUser")
    inner class SendToUser {

        @Test
        fun 연결이_없는_사용자에게_전송해도_오류가_발생하지_않는다() {
            val response = createNotificationResponse()

            notificationSseService.sendToUser(999L, response)
        }
    }

    @Nested
    @DisplayName("onMessage")
    inner class OnMessage {

        @Test
        fun Redis_메시지를_수신하여_SSE로_전달한다() {
            val notification = createNotificationResponse()
            val sseMessage = SseNotificationMessage(userId = 1L, notification = notification)
            val json = objectMapper.writeValueAsString(sseMessage)

            val emitter = SseEmitter(30000L)
            sseEmitterRepository.save(1L, emitter)

            val redisMessage = object : org.springframework.data.redis.connection.Message {
                override fun getBody(): ByteArray = json.toByteArray()
                override fun getChannel(): ByteArray = NotificationSseService.CHANNEL_NAME.toByteArray()
            }

            notificationSseService.onMessage(redisMessage, null)
        }
    }
}
