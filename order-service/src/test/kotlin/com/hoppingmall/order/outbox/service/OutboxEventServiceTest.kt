package com.hoppingmall.order.outbox.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.repository.OutboxEventRepository
import com.hoppingmall.outbox.service.OutboxEventService
import com.hoppingmall.outbox.service.OutboxEventWriter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("OutboxEventWriter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OutboxEventServiceTest {

    @Mock
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @InjectMocks
    private lateinit var service: OutboxEventWriter

    @Test
    fun 이벤트_저장_시_Outbox에_PENDING_상태로_저장된다() {
        val eventData = mapOf("key" to "value")
        whenever(objectMapper.writeValueAsString(eventData)).thenReturn("""{"key":"value"}""")
        whenever(outboxEventRepository.existsDuplicateEvent(any(), any(), any(), any(), any())).thenReturn(false)
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer { it.arguments[0] as OutboxEvent }

        service.saveEvent(
            aggregateType = "Order",
            aggregateId = "1",
            eventType = "OrderCreated",
            eventData = eventData,
            topic = "order-events",
            partitionKey = "1"
        )

        verify(outboxEventRepository).save(any<OutboxEvent>())
    }

    @Test
    fun 중복_이벤트는_저장하지_않는다() {
        val eventData = mapOf("key" to "value")
        whenever(objectMapper.writeValueAsString(eventData)).thenReturn("""{"key":"value"}""")
        whenever(outboxEventRepository.existsDuplicateEvent(any(), any(), any(), any(), any())).thenReturn(true)

        service.saveEvent(
            aggregateType = "Order",
            aggregateId = "1",
            eventType = "OrderCreated",
            eventData = eventData,
            topic = "order-events",
            partitionKey = "1"
        )

        verify(outboxEventRepository, never()).save(any<OutboxEvent>())
    }

    @Test
    fun 이벤트_통계를_반환한다() {
        val statsRepository = outboxEventRepository
        whenever(statsRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(5L)
        whenever(statsRepository.countByStatus(OutboxStatus.RETRYING)).thenReturn(2L)
        whenever(statsRepository.countByStatus(OutboxStatus.PUBLISHED)).thenReturn(100L)
        whenever(statsRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(1L)

        val outboxEventService = OutboxEventService(statsRepository, service)
        val stats = outboxEventService.getEventStats()

        assertThat(stats["pending"]).isEqualTo(5L)
        assertThat(stats["retrying"]).isEqualTo(2L)
        assertThat(stats["published"]).isEqualTo(100L)
        assertThat(stats["failed"]).isEqualTo(1L)
    }

    @Test
    fun partitionKey_미지정_시_aggregateId를_사용한다() {
        val eventData = mapOf("key" to "value")
        whenever(objectMapper.writeValueAsString(eventData)).thenReturn("""{"key":"value"}""")
        whenever(outboxEventRepository.existsDuplicateEvent(any(), any(), any(), any(), any())).thenReturn(false)
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer {
            val saved = it.arguments[0] as OutboxEvent
            assertThat(saved.partitionKey).isEqualTo("123")
            saved
        }

        service.saveEvent(
            aggregateType = "Order",
            aggregateId = "123",
            eventType = "OrderCreated",
            eventData = eventData,
            topic = "order-events"
        )

        verify(outboxEventRepository).save(any<OutboxEvent>())
    }
}
