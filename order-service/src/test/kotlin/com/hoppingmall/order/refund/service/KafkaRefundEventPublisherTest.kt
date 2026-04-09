package com.hoppingmall.order.refund.service

import com.hoppingmall.common.KafkaTopics
import com.hoppingmall.order.refund.dto.event.RefundCompletedEvent
import com.hoppingmall.order.refund.dto.event.RefundItemEvent
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import java.math.BigDecimal

@DisplayName("KafkaRefundEventPublisher")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class KafkaRefundEventPublisherTest {

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @InjectMocks
    private lateinit var publisher: KafkaRefundEventPublisher

    @Test
    fun 환불_완료_이벤트를_발행한다() {
        val event = RefundCompletedEvent(
            eventId = "evt-1",
            refundId = 1L,
            orderId = 10L,
            paymentId = 20L,
            buyerId = 100L,
            refundAmount = BigDecimal("10000"),
            pointRefundAmount = BigDecimal("100"),
            isFullRefund = false,
            couponId = null,
            items = listOf(
                RefundItemEvent(productId = 200L, quantity = 1, refundPrice = BigDecimal("10000"))
            )
        )

        publisher.publishRefundCompletedEvent(event)

        verify(transactionalEventPublisher).publishEvent(
            aggregateType = eq("Refund"),
            aggregateId = eq("1"),
            eventType = eq("RefundCompleted"),
            eventData = any(),
            topic = eq(KafkaTopics.REFUND_COMPLETION),
            partitionKey = eq("1")
        )
    }

    @Test
    fun 전액환불_이벤트를_발행한다() {
        val event = RefundCompletedEvent(
            eventId = "evt-2",
            refundId = 2L,
            orderId = 10L,
            paymentId = 20L,
            buyerId = 100L,
            refundAmount = BigDecimal("50000"),
            pointRefundAmount = BigDecimal("500"),
            isFullRefund = true,
            couponId = 5L,
            items = listOf(
                RefundItemEvent(productId = 200L, quantity = 2, refundPrice = BigDecimal("20000")),
                RefundItemEvent(productId = 300L, quantity = 1, refundPrice = BigDecimal("30000"))
            )
        )

        publisher.publishRefundCompletedEvent(event)

        verify(transactionalEventPublisher).publishEvent(
            aggregateType = eq("Refund"),
            aggregateId = eq("2"),
            eventType = eq("RefundCompleted"),
            eventData = any(),
            topic = eq(KafkaTopics.REFUND_COMPLETION),
            partitionKey = eq("2")
        )
    }
}
