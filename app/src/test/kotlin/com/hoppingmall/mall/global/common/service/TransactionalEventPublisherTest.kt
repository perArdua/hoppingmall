package com.hoppingmall.mall.global.common.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.transaction.annotation.Transactional
import kotlin.test.Test as KTest

@DisplayName("TransactionalEventPublisher")
@DisplayNameGeneration(ReplaceUnderscores::class)
class TransactionalEventPublisherTest {

    private val outboxEventService: OutboxEventService = mock()
    private val transactionalEventPublisher = TransactionalEventPublisher(outboxEventService)

    @Test
    fun WAL_패턴으로_이벤트_발행() {
        val eventData = mapOf("paymentId" to 123L, "amount" to 1000L)
        
        transactionalEventPublisher.publishEvent(
            aggregateType = "Payment",
            aggregateId = "123",
            eventType = "PaymentCompleted",
            eventData = eventData,
            topic = "payment",
            partitionKey = "order-123"
        )
        
        verify(outboxEventService).saveEvent(
            aggregateType = "Payment",
            aggregateId = "123",
            eventType = "PaymentCompleted",
            eventData = eventData,
            topic = "payment",
            partitionKey = "order-123"
        )
    }
    
    @Test
    fun 트랜잭션_내에서_이벤트_저장_확인() {
        val eventData = mapOf("userId" to 456L)
        
        // @Transactional 애노테이션이 있는지 확인
        val method = TransactionalEventPublisher::class.java
            .getMethod("publishEvent", String::class.java, String::class.java, 
                      String::class.java, Any::class.java, String::class.java, String::class.java)
        
        val transactionalAnnotation = method.getAnnotation(Transactional::class.java)
        kotlin.test.assertNotNull(transactionalAnnotation, "publishEvent 메서드에 @Transactional 애노테이션이 있어야 합니다")
        
        transactionalEventPublisher.publishEvent(
            aggregateType = "User",
            aggregateId = "456",
            eventType = "UserRegistered",
            eventData = eventData,
            topic = "user",
            partitionKey = "456"
        )
        
        verify(outboxEventService).saveEvent(any(), any(), any(), any(), any(), any())
    }
}