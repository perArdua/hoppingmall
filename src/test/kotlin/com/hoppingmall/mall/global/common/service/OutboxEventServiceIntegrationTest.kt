package com.hoppingmall.mall.global.common.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.global.common.domain.OutboxEvent
import com.hoppingmall.mall.global.common.domain.OutboxStatus
import com.hoppingmall.mall.global.common.repository.OutboxEventRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.mockito.kotlin.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
@DisplayName("OutboxEventService 통합 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class OutboxEventServiceIntegrationTest {

    @Mock
    private lateinit var outboxEventRepository: OutboxEventRepository
    
    @Mock
    private lateinit var objectMapper: ObjectMapper

    @Mock
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>
    
    private lateinit var outboxEventService: OutboxEventService

    @BeforeEach
    fun setUp() {
        outboxEventService = OutboxEventService(outboxEventRepository, kafkaTemplate, objectMapper)
        
        val mockSendResult = mockSendResult()
        val mockFuture = CompletableFuture.completedFuture(mockSendResult)
        lenient().`when`(kafkaTemplate.send(any<String>(), any<String>(), any<Any>())).thenReturn(mockFuture)
        lenient().`when`(kafkaTemplate.executeInTransaction<Any>(any())).thenAnswer { invocation ->
            val callback = invocation.arguments[0] as (KafkaTemplate<String, Any>) -> Any
            callback(kafkaTemplate)
        }
        lenient().`when`(objectMapper.writeValueAsString(any())).thenReturn("\"{\"test\": \"data\"}\"")
    }

    private fun mockSendResult(): SendResult<String, Any> {
        val mockMetadata = org.apache.kafka.clients.producer.RecordMetadata(
            org.apache.kafka.common.TopicPartition("test-topic", 0),
            0L, 0L, 0L, null, 0, 0
        )
        return SendResult(
            org.apache.kafka.clients.producer.ProducerRecord("test-topic", "key", "value"),
            mockMetadata
        )
    }

    @Test
    fun saveEvent_정상_저장() {
        val eventData = mapOf("orderId" to 123, "amount" to 50000)
        val savedEvent = OutboxEvent(
            aggregateType = "Payment",
            aggregateId = "123",
            eventType = "PaymentCompleted",
            eventData = "\"{\"test\": \"data\"}\"",
            topic = "payment"
        )
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenReturn(savedEvent)
        
        outboxEventService.saveEvent(
            aggregateType = "Payment",
            aggregateId = "123",
            eventType = "PaymentCompleted",
            eventData = eventData,
            topic = "payment"
        )
        
        verify(outboxEventRepository).save(any<OutboxEvent>())
        verify(objectMapper).writeValueAsString(eventData)
    }

    @Test
    fun getEventsByAggregateId_정상_조회() {
        val event1 = OutboxEvent(
            aggregateType = "Payment",
            aggregateId = "123",
            eventType = "PaymentCompleted",
            eventData = "\"{\"amount\": 10000}\"",
            topic = "payment"
        )
        
        whenever(outboxEventRepository.findByAggregateIdAndType("123", "Payment"))
            .thenReturn(listOf(event1))
        
        val paymentEvents = outboxEventService.getEventsByAggregateId("123", "Payment")
        assertEquals(1, paymentEvents.size)
        assertEquals("PaymentCompleted", paymentEvents.first().eventType)
        
        verify(outboxEventRepository).findByAggregateIdAndType("123", "Payment")
    }

    @Test
    fun getEventStats_상태별_통계_조회() {
        whenever(outboxEventRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(1L)
        whenever(outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED)).thenReturn(1L)
        whenever(outboxEventRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(1L)
        whenever(outboxEventRepository.countByStatus(OutboxStatus.RETRYING)).thenReturn(0L)
        
        val stats = outboxEventService.getEventStats()
        assertEquals(1L, stats["pending"])
        assertEquals(1L, stats["published"])
        assertEquals(1L, stats["failed"])
        assertEquals(0L, stats["retrying"])
        
        verify(outboxEventRepository).countByStatus(OutboxStatus.PENDING)
        verify(outboxEventRepository).countByStatus(OutboxStatus.PUBLISHED)
        verify(outboxEventRepository).countByStatus(OutboxStatus.FAILED)
        verify(outboxEventRepository).countByStatus(OutboxStatus.RETRYING)
    }
}