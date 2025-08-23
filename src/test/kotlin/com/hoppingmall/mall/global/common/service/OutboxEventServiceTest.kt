package com.hoppingmall.mall.global.common.service

import com.hoppingmall.mall.global.common.domain.OutboxEvent
import com.hoppingmall.mall.global.common.domain.OutboxStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("OutboxEventService")
@DisplayNameGeneration(ReplaceUnderscores::class)
class OutboxEventServiceTest {

    @Test
    fun OutboxEvent_생성_테스트() {
        // OutboxEvent 도메인 객체 생성 테스트
        val event = OutboxEvent(
            aggregateType = "Payment",
            aggregateId = "123", 
            eventType = "PaymentCompleted",
            eventData = """{"key": "value"}""",
            topic = "payment",
            partitionKey = "order-123"
        )
        
        assertEquals("Payment", event.aggregateType)
        assertEquals("123", event.aggregateId)
        assertEquals("PaymentCompleted", event.eventType)
        assertEquals("payment", event.topic)
        assertEquals("order-123", event.partitionKey)
        assertEquals(OutboxStatus.PENDING, event.status)
    }

    @Test
    fun OutboxEvent_상태_변경_테스트() {
        val event = OutboxEvent(
            aggregateType = "Payment",
            aggregateId = "123",
            eventType = "PaymentCompleted", 
            eventData = """{"key": "value"}""",
            topic = "payment",
            partitionKey = "order-123"
        )
        
        // 처리 완료로 마킹
        event.markAsProcessed()
        assertEquals(OutboxStatus.PUBLISHED, event.status)
        assertEquals(true, event.processed)
        
        // 실패로 마킹  
        val failEvent = OutboxEvent(
            aggregateType = "Payment",
            aggregateId = "456", 
            eventType = "PaymentFailed",
            eventData = """{"error": "timeout"}""",
            topic = "payment"
        )
        
        failEvent.markAsFailed("Connection timeout")
        assertEquals(OutboxStatus.FAILED, failEvent.status)
        assertEquals(1, failEvent.retryCount)
        assertEquals("Connection timeout", failEvent.errorMessage)
    }
}