package com.hoppingmall.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.connection.DefaultMessage
import org.springframework.data.redis.listener.RedisMessageListenerContainer

@ExtendWith(MockitoExtension::class)
@DisplayName("앱 JSON 이름 규칙이 snake_case여도 Redis SSE 메시지는 지금 형식을 읽는다")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SseNotificationMessageFormatTest {

    @Mock
    private lateinit var sseEmitterRepository: SseEmitterRepository

    @Mock
    private lateinit var redisMessageListenerContainer: RedisMessageListenerContainer

    private val snakeCaseMapper = ObjectMapper().apply {
        findAndRegisterModules()
        propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
    }

    @Test
    fun 배포_중_옛_인스턴스가_보낸_camelCase_메시지를_받아_사용자에게_보낸다() {
        val service = NotificationSseService(sseEmitterRepository, redisMessageListenerContainer, snakeCaseMapper)
        val camelCaseBody = """
            {"userId":31,"notification":{"id":5,"type":"PAYMENT_COMPLETED","title":"결제 완료","content":"내용",
            "metadata":null,"isRead":false,"createdAt":"2026-11-16T09:00:00"}}
        """.trimIndent()
        whenever(sseEmitterRepository.findByUserId(31L)).thenReturn(emptyList())

        service.onMessage(DefaultMessage(ByteArray(0), camelCaseBody.toByteArray()), null)

        verify(sseEmitterRepository).findByUserId(31L)
    }
}
