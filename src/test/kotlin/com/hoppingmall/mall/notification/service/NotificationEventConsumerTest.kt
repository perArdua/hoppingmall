package com.hoppingmall.mall.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.hoppingmall.mall.notification.domain.Notification
import com.hoppingmall.mall.notification.domain.NotificationRepository
import com.hoppingmall.mall.notification.dto.event.NotificationEvent
import com.hoppingmall.mall.notification.enum.NotificationType
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import kotlin.test.assertEquals

@DisplayName("NotificationEventConsumer")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationEventConsumerTest {

    private val notificationRepository: NotificationRepository = mock()
    private val redisTemplate: RedisTemplate<String, String> = mock()
    private val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
    }
    private val notificationEventConsumer = NotificationEventConsumer(notificationRepository, redisTemplate, objectMapper)

    @Test
    fun 결제_완료_알림_이벤트를_처리한다() {
        val metadata = """
            {
                "orderId": 123,
                "paymentId": 456,
                "amount": "50000",
                "method": "CREDIT_CARD",
                "transactionId": "tx_123456"
            }
        """.trimIndent()

        val event = NotificationEvent(
            eventId = "EVENT-1",
            userId = 1L,
            type = NotificationType.PAYMENT_COMPLETED,
            title = "결제가 완료되었습니다",
            content = "주문번호 123의 결제가 성공적으로 완료되었습니다. 결제 금액: 50000원",
            metadata = metadata
        )

        val captor = argumentCaptor<Notification>()
        whenever(notificationRepository.save(any<Notification>())).thenAnswer { invocation ->
            (invocation.arguments[0] as Notification).withId(1L)
        }

        notificationEventConsumer.handleNotificationEvent(event)

        verify(notificationRepository).save(captor.capture())
        val savedNotification = captor.firstValue
        assertEquals("EVENT-1", savedNotification.eventId)
        assertEquals(1L, savedNotification.userId)
        assertEquals(NotificationType.PAYMENT_COMPLETED, savedNotification.type)
        assertEquals("결제가 완료되었습니다", savedNotification.title)
        assertEquals("주문번호 123의 결제가 성공적으로 완료되었습니다. 결제 금액: 50000원", savedNotification.content)
        assertEquals(metadata, savedNotification.metadata)
        assertEquals(false, savedNotification.isRead)
        verify(redisTemplate).convertAndSend(any<String>(), any<String>())
    }

    @Test
    fun 포인트_적립_알림_이벤트를_처리한다() {
        val metadata = """
            {
                "orderId": 123,
                "paymentId": 456,
                "earnAmount": "100",
                "reason": "결제 완료",
                "currentBalance": "1000"
            }
        """.trimIndent()

        val event = NotificationEvent(
            eventId = "EVENT-2",
            userId = 2L,
            type = NotificationType.POINT_EARNED,
            title = "포인트가 적립되었습니다",
            content = "주문번호 123의 포인트 100점이 적립되었습니다. 현재 잔액: 1000점",
            metadata = metadata
        )

        val captor = argumentCaptor<Notification>()
        whenever(notificationRepository.save(any<Notification>())).thenAnswer { invocation ->
            (invocation.arguments[0] as Notification).withId(2L)
        }

        notificationEventConsumer.handleNotificationEvent(event)

        verify(notificationRepository).save(captor.capture())
        val savedNotification = captor.firstValue
        assertEquals("EVENT-2", savedNotification.eventId)
        assertEquals(2L, savedNotification.userId)
        assertEquals(NotificationType.POINT_EARNED, savedNotification.type)
        assertEquals("포인트가 적립되었습니다", savedNotification.title)
        assertEquals("주문번호 123의 포인트 100점이 적립되었습니다. 현재 잔액: 1000점", savedNotification.content)
        assertEquals(metadata, savedNotification.metadata)
        assertEquals(false, savedNotification.isRead)
        verify(redisTemplate).convertAndSend(any<String>(), any<String>())
    }

    @Test
    fun 중복_알림_이벤트는_저장하지_않는다() {
        val event = NotificationEvent(
            eventId = "EVENT-3",
            userId = 3L,
            type = NotificationType.PAYMENT_COMPLETED,
            title = "중복 테스트",
            content = "중복 처리 방지",
            metadata = null
        )

        whenever(notificationRepository.existsByEventId(event.eventId)).thenReturn(true)

        notificationEventConsumer.handleNotificationEvent(event)

        verify(notificationRepository, never()).save(any())
        verify(redisTemplate, never()).convertAndSend(any<String>(), any<String>())
    }
}
