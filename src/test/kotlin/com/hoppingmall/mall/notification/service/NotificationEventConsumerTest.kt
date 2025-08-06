package com.hoppingmall.mall.notification.service

import com.hoppingmall.mall.notification.domain.Notification
import com.hoppingmall.mall.notification.domain.NotificationRepository
import com.hoppingmall.mall.notification.dto.event.NotificationEvent
import com.hoppingmall.mall.notification.enum.NotificationType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import kotlin.test.assertEquals

class NotificationEventConsumerTest {
    
    private val notificationRepository: NotificationRepository = mock()
    private val notificationEventConsumer = NotificationEventConsumer(notificationRepository)
    
    @Test
    fun `결제 완료 알림 이벤트를 처리한다`() {
        // given
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
            userId = 1L,
            type = NotificationType.PAYMENT_COMPLETED,
            title = "결제가 완료되었습니다",
            content = "주문번호 123의 결제가 성공적으로 완료되었습니다. 결제 금액: 50000원",
            metadata = metadata
        )
        
        val captor = argumentCaptor<Notification>()
        
        // when
        notificationEventConsumer.handleNotificationEvent(event)
        
        // then
        verify(notificationRepository).save(captor.capture())
        val savedNotification = captor.firstValue
        assertEquals(1L, savedNotification.userId)
        assertEquals(NotificationType.PAYMENT_COMPLETED, savedNotification.type)
        assertEquals("결제가 완료되었습니다", savedNotification.title)
        assertEquals("주문번호 123의 결제가 성공적으로 완료되었습니다. 결제 금액: 50000원", savedNotification.content)
        assertEquals(metadata, savedNotification.metadata)
        assertEquals(false, savedNotification.isRead)
    }
    
    @Test
    fun `포인트 적립 알림 이벤트를 처리한다`() {
        // given
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
            userId = 2L,
            type = NotificationType.POINT_EARNED,
            title = "포인트가 적립되었습니다",
            content = "주문번호 123의 포인트 100점이 적립되었습니다. 현재 잔액: 1000점",
            metadata = metadata
        )
        
        val captor = argumentCaptor<Notification>()
        
        // when
        notificationEventConsumer.handleNotificationEvent(event)
        
        // then
        verify(notificationRepository).save(captor.capture())
        val savedNotification = captor.firstValue
        assertEquals(2L, savedNotification.userId)
        assertEquals(NotificationType.POINT_EARNED, savedNotification.type)
        assertEquals("포인트가 적립되었습니다", savedNotification.title)
        assertEquals("주문번호 123의 포인트 100점이 적립되었습니다. 현재 잔액: 1000점", savedNotification.content)
        assertEquals(metadata, savedNotification.metadata)
        assertEquals(false, savedNotification.isRead)
    }
} 