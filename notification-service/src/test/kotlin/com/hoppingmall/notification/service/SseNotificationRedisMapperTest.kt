package com.hoppingmall.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.hoppingmall.notification.dto.response.NotificationResponse
import com.hoppingmall.notification.enums.NotificationType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("Redis SSE 메시지 형식")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SseNotificationRedisMapperTest {

    private val snakeCaseMapper = ObjectMapper().apply {
        findAndRegisterModules()
        propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
    }

    private val message = SseNotificationMessage(
        userId = 31L,
        notification = NotificationResponse(
            id = 5L,
            type = NotificationType.PAYMENT_COMPLETED,
            title = "결제 완료",
            content = "내용",
            metadata = null,
            isRead = false,
            createdAt = LocalDateTime.of(2026, 11, 16, 9, 0)
        )
    )

    @Test
    fun 앱_이름_규칙이_snake_case여도_Redis_메시지는_camelCase로_쓴다() {
        val json = snakeCaseMapper.readTree(snakeCaseMapper.writeValueAsString(message.toRedisMessage()))

        assertThat(json.fieldNames().asSequence().toList()).containsExactlyInAnyOrder("userId", "notification")
        assertThat(json.path("notification").fieldNames().asSequence().toList())
            .containsExactlyInAnyOrder("id", "type", "title", "content", "metadata", "isRead", "createdAt")
    }

    @Test
    fun 쓴_메시지를_다시_읽으면_같은_알림이다() {
        val body = snakeCaseMapper.writeValueAsString(message.toRedisMessage())

        assertThat(snakeCaseMapper.readValue(body, RedisSseNotificationMessage::class.java).toMessage()).isEqualTo(message)
    }
}
