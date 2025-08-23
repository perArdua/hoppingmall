package com.hoppingmall.mall.global.common.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.global.common.config.TestKafkaConfig
import com.hoppingmall.mall.global.common.domain.OutboxEvent
import com.hoppingmall.mall.global.common.domain.OutboxStatus
import com.hoppingmall.mall.global.common.repository.OutboxEventRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestKafkaConfig::class)
@ActiveProfiles("test")
@DisplayName("OutboxEventService 통합 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
@Transactional
class OutboxEventServiceIntegrationTest {

    @Autowired
    private lateinit var outboxEventService: OutboxEventService
    
    @Autowired
    private lateinit var outboxEventRepository: OutboxEventRepository
    
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        outboxEventRepository.deleteAll()
    }

    @Test
    fun saveEvent_정상_저장() {
        val eventData = mapOf("orderId" to 123, "amount" to 50000)
        
        outboxEventService.saveEvent(
            aggregateType = "Payment",
            aggregateId = "123",
            eventType = "PaymentCompleted",
            eventData = eventData,
            topic = "payment"
        )
        
        val savedEvents = outboxEventRepository.findAll()
        assertEquals(1, savedEvents.size)
        
        val event = savedEvents.first()
        assertEquals("Payment", event.aggregateType)
        assertEquals("123", event.aggregateId)
        assertEquals("PaymentCompleted", event.eventType)
        assertEquals("payment", event.topic)
        assertEquals(OutboxStatus.PENDING, event.status)
        assertEquals("123", event.partitionKey)
        assertNotNull(event.eventData)
        assertTrue(event.eventData.contains("orderId"))
    }

    @Test
    fun getEventsByAggregateId_정상_조회() {
        val event1 = OutboxEvent(
            aggregateType = "Payment",
            aggregateId = "123",
            eventType = "PaymentCompleted",
            eventData = """{"amount": 10000}""",
            topic = "payment"
        )
        
        val event2 = OutboxEvent(
            aggregateType = "Order",
            aggregateId = "456",
            eventType = "OrderCreated",
            eventData = """{"productId": 789}""",
            topic = "order"
        )
        
        outboxEventRepository.saveAll(listOf(event1, event2))
        
        val paymentEvents = outboxEventService.getEventsByAggregateId("123", "Payment")
        assertEquals(1, paymentEvents.size)
        assertEquals("PaymentCompleted", paymentEvents.first().eventType)
        
        val orderEvents = outboxEventService.getEventsByAggregateId("456", "Order")
        assertEquals(1, orderEvents.size)
        assertEquals("OrderCreated", orderEvents.first().eventType)
    }

    @Test
    fun getEventStats_상태별_통계_조회() {
        val pendingEvent = OutboxEvent(
            aggregateType = "Payment",
            aggregateId = "1",
            eventType = "PaymentCompleted",
            eventData = """{"amount": 10000}""",
            topic = "payment"
        )
        
        val publishedEvent = OutboxEvent(
            aggregateType = "Order",
            aggregateId = "2",
            eventType = "OrderCreated",
            eventData = """{"productId": 789}""",
            topic = "order"
        ).apply { markAsProcessed() }
        
        val failedEvent = OutboxEvent(
            aggregateType = "Point",
            aggregateId = "3",
            eventType = "PointEarned",
            eventData = """{"amount": 100}""",
            topic = "point"
        ).apply { markAsFailed("Connection timeout") }
        
        outboxEventRepository.saveAll(listOf(pendingEvent, publishedEvent, failedEvent))
        
        val stats = outboxEventService.getEventStats()
        assertEquals(1L, stats["pending"])
        assertEquals(1L, stats["published"])
        assertEquals(1L, stats["failed"])
        assertEquals(0L, stats["retrying"])
    }
}