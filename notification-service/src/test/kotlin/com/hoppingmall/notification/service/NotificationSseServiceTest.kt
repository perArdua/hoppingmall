package com.hoppingmall.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.notification.dto.response.NotificationResponse
import com.hoppingmall.notification.enums.NotificationType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.connection.DefaultMessage
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@DisplayName("NotificationSseService")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationSseServiceTest {

    @Mock
    private lateinit var sseEmitterRepository: SseEmitterRepository

    @Mock
    private lateinit var redisMessageListenerContainer: RedisMessageListenerContainer

    private val objectMapper = ObjectMapper().apply {
        findAndRegisterModules()
    }

    private lateinit var notificationSseService: NotificationSseService

    @BeforeEach
    fun setUp() {
        notificationSseService = NotificationSseService(
            sseEmitterRepository,
            redisMessageListenerContainer,
            objectMapper
        )
    }

    @Test
    fun SSE_연결을_생성한다() {
        val emitter = notificationSseService.connect(1L)

        assertThat(emitter).isNotNull()
        verify(sseEmitterRepository).save(eq(1L), any())
    }

    @Test
    fun subscribe_호출시_리스너를_등록한다() {
        notificationSseService.subscribe()

        verify(redisMessageListenerContainer).addMessageListener(eq(notificationSseService), any<org.springframework.data.redis.listener.ChannelTopic>())
    }

    @Test
    fun unsubscribe_호출시_리스너를_해제한다() {
        notificationSseService.unsubscribe()

        verify(redisMessageListenerContainer).removeMessageListener(notificationSseService)
    }

    @Test
    fun 사용자에게_알림을_전송한다() {
        val emitter = SseEmitter(1800000L)
        val response = createNotificationResponse()
        whenever(sseEmitterRepository.findByUserId(1L)).thenReturn(listOf(emitter))

        notificationSseService.sendToUser(1L, response)

        verify(sseEmitterRepository).findByUserId(1L)
    }

    @Test
    fun 전송할_emitter가_없으면_아무_동작도_하지_않는다() {
        val response = createNotificationResponse()
        whenever(sseEmitterRepository.findByUserId(1L)).thenReturn(emptyList())

        notificationSseService.sendToUser(1L, response)

        verify(sseEmitterRepository).findByUserId(1L)
    }

    @Test
    fun Redis_메시지를_수신하여_사용자에게_전달한다() {
        val response = createNotificationResponse()
        val sseMessage = SseNotificationMessage(userId = 1L, notification = response)
        val messageBody = objectMapper.writeValueAsBytes(sseMessage)
        val message = DefaultMessage(ByteArray(0), messageBody)
        whenever(sseEmitterRepository.findByUserId(1L)).thenReturn(emptyList())

        notificationSseService.onMessage(message, null)

        verify(sseEmitterRepository).findByUserId(1L)
    }

    @Test
    fun Redis_메시지_처리_실패시_예외를_던지지_않는다() {
        val invalidMessage = DefaultMessage(ByteArray(0), "invalid-json".toByteArray())

        notificationSseService.onMessage(invalidMessage, null)
    }

    private fun createNotificationResponse(): NotificationResponse {
        return NotificationResponse(
            id = 1L,
            type = NotificationType.PAYMENT_COMPLETED,
            title = "테스트",
            content = "테스트 내용",
            metadata = null,
            isRead = false,
            createdAt = LocalDateTime.now()
        )
    }
}
