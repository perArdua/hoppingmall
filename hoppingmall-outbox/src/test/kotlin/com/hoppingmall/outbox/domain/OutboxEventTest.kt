package com.hoppingmall.outbox.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("OutboxEvent")
@DisplayNameGeneration(ReplaceUnderscores::class)
class OutboxEventTest {

    @Nested
    @DisplayName("생성")
    inner class Creation {

        @Test
        fun OutboxEvent_생성_시_기본_상태는_PENDING이다() {
            val event = createOutboxEvent()

            assertEquals("Payment", event.aggregateType)
            assertEquals("123", event.aggregateId)
            assertEquals("PaymentCompleted", event.eventType)
            assertEquals("payment", event.topic)
            assertEquals("order-123", event.partitionKey)
            assertEquals(OutboxStatus.PENDING, event.status)
            assertFalse(event.processed)
            assertEquals(0, event.retryCount)
            assertNull(event.processedAt)
            assertNull(event.errorMessage)
        }

        @Test
        fun partitionKey_없이_생성할_수_있다() {
            val event = createOutboxEvent(partitionKey = null)

            assertNull(event.partitionKey)
            assertEquals(OutboxStatus.PENDING, event.status)
        }
    }

    @Nested
    @DisplayName("markAsProcessed")
    inner class MarkAsProcessed {

        @Test
        fun 상태를_PUBLISHED로_변경하고_처리_시간을_설정한다() {
            val event = createOutboxEvent()

            event.markAsProcessed()

            assertEquals(OutboxStatus.PUBLISHED, event.status)
            assertTrue(event.processed)
            assertNotNull(event.processedAt)
        }
    }

    @Nested
    @DisplayName("markAsFailed")
    inner class MarkAsFailed {

        @Test
        fun 상태를_FAILED로_변경하고_재시도_카운트를_증가시킨다() {
            val event = createOutboxEvent()

            event.markAsFailed("Connection timeout")

            assertEquals(OutboxStatus.FAILED, event.status)
            assertEquals(1, event.retryCount)
            assertEquals("Connection timeout", event.errorMessage)
            assertFalse(event.processed)
        }

        @Test
        fun 여러번_호출하면_재시도_카운트가_누적된다() {
            val event = createOutboxEvent()

            event.markAsFailed("error 1")
            event.markAsFailed("error 2")
            event.markAsFailed("error 3")

            assertEquals(3, event.retryCount)
            assertEquals("error 3", event.errorMessage)
        }
    }

    @Nested
    @DisplayName("markAsFailedPermanently")
    inner class MarkAsFailedPermanently {

        @Test
        fun 상태를_FAILED로_변경하고_processed를_true로_설정한다() {
            val event = createOutboxEvent()

            event.markAsFailedPermanently("최대 재시도 초과")

            assertEquals(OutboxStatus.FAILED, event.status)
            assertTrue(event.processed)
            assertNotNull(event.processedAt)
            assertEquals(1, event.retryCount)
            assertEquals("최대 재시도 초과", event.errorMessage)
        }
    }

    @Nested
    @DisplayName("markAsRetrying")
    inner class MarkAsRetrying {

        @Test
        fun 상태를_RETRYING으로_변경한다() {
            val event = createOutboxEvent()

            event.markAsRetrying()

            assertEquals(OutboxStatus.RETRYING, event.status)
            assertFalse(event.processed)
        }
    }

    @Nested
    @DisplayName("updatedAt 갱신")
    inner class UpdatedAtRefresh {

        @Test
        fun markAsProcessed_호출_시_updatedAt이_갱신된다() {
            val event = createOutboxEvent()
            val before = LocalDateTime.now().minusSeconds(1)

            event.markAsProcessed()

            assertTrue(event.updatedAt.isAfter(before))
        }

        @Test
        fun markAsFailed_호출_시_updatedAt이_갱신된다() {
            val event = createOutboxEvent()
            val before = LocalDateTime.now().minusSeconds(1)

            event.markAsFailed("error")

            assertTrue(event.updatedAt.isAfter(before))
        }

        @Test
        fun markAsRetrying_호출_시_updatedAt이_갱신된다() {
            val event = createOutboxEvent()
            val before = LocalDateTime.now().minusSeconds(1)

            event.markAsRetrying()

            assertTrue(event.updatedAt.isAfter(before))
        }

        @Test
        fun markAsFailedPermanently_호출_시_processedAt이_설정된다() {
            val event = createOutboxEvent()
            val before = LocalDateTime.now().minusSeconds(1)

            event.markAsFailedPermanently("permanent error")

            assertNotNull(event.processedAt)
            assertTrue(event.processedAt!!.isAfter(before))
        }
    }

    private fun createOutboxEvent(
        aggregateType: String = "Payment",
        aggregateId: String = "123",
        eventType: String = "PaymentCompleted",
        eventData: String = """{"key": "value"}""",
        topic: String = "payment",
        partitionKey: String? = "order-123"
    ): OutboxEvent {
        return OutboxEvent(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            eventData = eventData,
            topic = topic,
            partitionKey = partitionKey
        )
    }
}
