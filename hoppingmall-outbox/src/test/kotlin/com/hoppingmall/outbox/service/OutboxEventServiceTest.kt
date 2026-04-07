package com.hoppingmall.outbox.service

import com.hoppingmall.outbox.domain.OutboxEvent
import com.hoppingmall.outbox.domain.OutboxStatus
import com.hoppingmall.outbox.repository.OutboxEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("OutboxEventService")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OutboxEventServiceTest {

    @Mock
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Mock
    private lateinit var outboxEventWriter: OutboxEventWriter

    @InjectMocks
    private lateinit var outboxEventService: OutboxEventService

    @Nested
    @DisplayName("saveEvent")
    inner class SaveEvent {

        @Test
        fun OutboxEventWriter에_이벤트_저장을_위임한다() {
            val eventData = mapOf("orderId" to 123, "amount" to 50000)

            outboxEventService.saveEvent(
                aggregateType = "Payment",
                aggregateId = "123",
                eventType = "PaymentCompleted",
                eventData = eventData,
                topic = "payment",
                partitionKey = "order-123"
            )

            verify(outboxEventWriter).saveEvent(
                aggregateType = "Payment",
                aggregateId = "123",
                eventType = "PaymentCompleted",
                eventData = eventData,
                topic = "payment",
                partitionKey = "order-123"
            )
        }

        @Test
        fun partitionKey_없이_저장을_위임한다() {
            val eventData = mapOf("orderId" to 456)

            outboxEventService.saveEvent(
                aggregateType = "Order",
                aggregateId = "456",
                eventType = "OrderCreated",
                eventData = eventData,
                topic = "order-events"
            )

            verify(outboxEventWriter).saveEvent(
                aggregateType = "Order",
                aggregateId = "456",
                eventType = "OrderCreated",
                eventData = eventData,
                topic = "order-events",
                partitionKey = null
            )
        }
    }

    @Nested
    @DisplayName("getEventsByAggregateId")
    inner class GetEventsByAggregateId {

        @Test
        fun aggregateId와_aggregateType으로_이벤트를_조회한다() {
            val event = OutboxEvent(
                aggregateType = "Payment",
                aggregateId = "123",
                eventType = "PaymentCompleted",
                eventData = """{"amount": 10000}""",
                topic = "payment"
            )
            whenever(outboxEventRepository.findByAggregateIdAndType("123", "Payment"))
                .thenReturn(listOf(event))

            val result = outboxEventService.getEventsByAggregateId("123", "Payment")

            assertEquals(1, result.size)
            assertEquals("PaymentCompleted", result.first().eventType)
            verify(outboxEventRepository).findByAggregateIdAndType("123", "Payment")
        }

        @Test
        fun 결과가_없으면_빈_리스트를_반환한다() {
            whenever(outboxEventRepository.findByAggregateIdAndType("999", "Payment"))
                .thenReturn(emptyList())

            val result = outboxEventService.getEventsByAggregateId("999", "Payment")

            assertEquals(0, result.size)
        }
    }

    @Nested
    @DisplayName("getEventStats")
    inner class GetEventStats {

        @Test
        fun 상태별_통계를_조회한다() {
            whenever(outboxEventRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(5L)
            whenever(outboxEventRepository.countByStatus(OutboxStatus.RETRYING)).thenReturn(2L)
            whenever(outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED)).thenReturn(10L)
            whenever(outboxEventRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(1L)

            val stats = outboxEventService.getEventStats()

            assertEquals(5L, stats["pending"])
            assertEquals(2L, stats["retrying"])
            assertEquals(10L, stats["published"])
            assertEquals(1L, stats["failed"])
            verify(outboxEventRepository).countByStatus(OutboxStatus.PENDING)
            verify(outboxEventRepository).countByStatus(OutboxStatus.RETRYING)
            verify(outboxEventRepository).countByStatus(OutboxStatus.PUBLISHED)
            verify(outboxEventRepository).countByStatus(OutboxStatus.FAILED)
        }
    }
}
