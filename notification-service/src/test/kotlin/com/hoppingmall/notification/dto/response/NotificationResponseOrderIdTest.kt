package com.hoppingmall.notification.dto.response

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hoppingmall.notification.domain.Notification
import com.hoppingmall.notification.enums.NotificationType
import com.hoppingmall.notification.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("알림 응답의 주문 id")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationResponseOrderIdTest {

    private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    @Test
    fun metadata의_orderId를_주문_id로_준다() {
        val json = responseJson("""{"orderId":42,"paymentId":7}""")

        assertThat(json.path("orderId").isIntegralNumber).isTrue()
        assertThat(json.path("orderId").asLong()).isEqualTo(42L)
        assertThat(json.path("metadata").asText()).isEqualTo("""{"orderId":42,"paymentId":7}""")
    }

    @Test
    fun metadata의_snake_case_order_id와_숫자_문자열도_읽는다() {
        assertThat(responseJson("""{"order_id":43}""").path("orderId").asLong()).isEqualTo(43L)
        assertThat(responseJson("""{"orderId":"44"}""").path("orderId").asLong()).isEqualTo(44L)
    }

    @Test
    fun metadata가_없거나_JSON이_아니거나_주문_id가_맞지_않으면_주문_id는_null이다() {
        listOf(
            null,
            "",
            "not-json",
            "[1,2]",
            """{"paymentId":7}""",
            """{"orderId":"abc"}""",
            """{"orderId":0}""",
            """{"orderId":-1}""",
            """{"orderId":1.5}""",
            """{"orderId":{"id":1}}"""
        ).forEach { metadata ->
            val node = responseJson(metadata).path("orderId")
            assertThat(node.isNull).describedAs("metadata=%s", metadata).isTrue()
        }
    }

    private fun responseJson(metadata: String?): JsonNode {
        val notification = Notification(
            eventId = "event-1",
            userId = 1L,
            type = NotificationType.PAYMENT_COMPLETED,
            title = "결제가 완료되었습니다",
            content = "내용",
            metadata = metadata
        ).withId(1L)
        return objectMapper.valueToTree(NotificationResponse.from(notification))
    }
}
