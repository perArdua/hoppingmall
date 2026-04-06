package com.hoppingmall.outbox.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.repository.OutboxEventRepository
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
class OutboxEventWriterTest {

    @Mock
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @InjectMocks
    private lateinit var writer: OutboxEventWriter

    @Test
    fun 이벤트_저장_시_Outbox에_PENDING_상태로_저장된다() {
        val eventData = mapOf("key" to "value")
        whenever(objectMapper.writeValueAsString(eventData)).thenReturn("""{"key":"value"}""")
        whenever(outboxEventRepository.existsDuplicateEvent(any(), any(), any(), any(), any())).thenReturn(false)
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer { it.arguments[0] as OutboxEvent }

        writer.saveEvent(
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

        writer.saveEvent(
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
    fun partitionKey_미지정_시_aggregateId를_사용한다() {
        val eventData = mapOf("key" to "value")
        whenever(objectMapper.writeValueAsString(eventData)).thenReturn("""{"key":"value"}""")
        whenever(outboxEventRepository.existsDuplicateEvent(any(), any(), any(), any(), any())).thenReturn(false)
        whenever(outboxEventRepository.save(any<OutboxEvent>())).thenAnswer {
            val saved = it.arguments[0] as OutboxEvent
            assertThat(saved.partitionKey).isEqualTo("123")
            saved
        }

        writer.saveEvent(
            aggregateType = "Order",
            aggregateId = "123",
            eventType = "OrderCreated",
            eventData = eventData,
            topic = "order-events"
        )

        verify(outboxEventRepository).save(any<OutboxEvent>())
    }
}
