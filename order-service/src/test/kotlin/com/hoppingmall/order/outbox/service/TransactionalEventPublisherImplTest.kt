package com.hoppingmall.order.outbox.service

import com.hoppingmall.outbox.service.OutboxEventWriter
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify

@DisplayName("TransactionalEventPublisherImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class TransactionalEventPublisherImplTest {

    @Mock
    private lateinit var outboxEventWriter: OutboxEventWriter

    @InjectMocks
    private lateinit var publisher: TransactionalEventPublisherImpl

    @Test
    fun publishEvent_호출_시_OutboxEventWriter의_saveEvent를_위임한다() {
        val eventData = mapOf<String, Any>("orderId" to 1L, "status" to "CREATED")

        publisher.publishEvent(
            aggregateType = "Order",
            aggregateId = "1",
            eventType = "OrderCreated",
            eventData = eventData,
            topic = "order-events",
            partitionKey = "1"
        )

        verify(outboxEventWriter).saveEvent(
            aggregateType = "Order",
            aggregateId = "1",
            eventType = "OrderCreated",
            eventData = eventData,
            topic = "order-events",
            partitionKey = "1"
        )
    }
}
