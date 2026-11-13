package com.hoppingmall.order.refund.service

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.port.PaymentInfo
import com.hoppingmall.order.port.PaymentQueryPort
import com.hoppingmall.order.refund.domain.Refund
import com.hoppingmall.order.refund.domain.RefundItem
import com.hoppingmall.order.refund.domain.repository.RefundItemRepository
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.refund.dto.event.RefundCompletedEvent
import com.hoppingmall.order.refund.dto.event.RefundItemEvent
import com.hoppingmall.order.refund.dto.request.RefundCreateRequest
import com.hoppingmall.order.refund.dto.request.RefundItemRequest
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("환불 완료 이벤트 항목에 주문 항목 id를 싣는다")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundCompletedEventOrderItemIdTest {

    @Mock
    private lateinit var refundRepository: RefundRepository

    @Mock
    private lateinit var refundItemRepository: RefundItemRepository

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    @Mock
    private lateinit var paymentQueryPort: PaymentQueryPort

    @Mock
    private lateinit var shippingRepository: ShippingRepository

    @Mock
    private lateinit var refundEventPublisher: RefundEventPublisher

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Test
    fun 환불_완료_이벤트의_항목에_환불한_주문_항목_id가_들어간다() {
        whenever(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as TransactionCallback<Any?>
            callback.doInTransaction(mock())
        }
        val service = RefundCommandServiceImpl(
            refundRepository, refundItemRepository, orderRepository,
            orderItemRepository, paymentQueryPort, shippingRepository,
            refundEventPublisher, transactionTemplate
        )
        val order = Order.create(buyerId = 1L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        ReflectionTestUtils.setField(order, "id", 10L)
        val orderItem = OrderItem.create(
            orderId = 10L, sellerId = 5L, productId = 100L,
            productName = "테스트 상품", productPrice = BigDecimal("25000"), quantity = 2
        )
        ReflectionTestUtils.setField(orderItem, "id", ORDER_ITEM_ID)
        val refund = Refund.create(
            orderId = 10L, paymentId = 20L, buyerId = 1L, sellerId = 5L,
            reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
            refundAmount = BigDecimal("25000"), isFullRefund = false
        )
        ReflectionTestUtils.setField(refund, "id", 1L)
        val refundItem = RefundItem.create(
            refundId = 1L, orderItemId = ORDER_ITEM_ID, productId = 100L,
            productName = "테스트 상품", productPrice = BigDecimal("25000"), quantity = 1
        )
        ReflectionTestUtils.setField(refundItem, "id", 1L)
        whenever(paymentQueryPort.findByOrderId(10L)).thenReturn(
            PaymentInfo(
                id = 20L, orderId = 10L, amount = BigDecimal("50000"),
                pointAmount = BigDecimal("500"), couponId = null, status = "SUCCESS"
            )
        )
        whenever(orderRepository.findById(10L)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(10L)).thenReturn(listOf(orderItem))
        whenever(refundItemRepository.findRefundedQuantitiesByOrderId(10L)).thenReturn(emptyList())
        whenever(refundRepository.save(any<Refund>())).thenReturn(refund)
        whenever(refundItemRepository.saveAll(any<List<RefundItem>>())).thenReturn(listOf(refundItem))

        service.requestRefund(
            1L,
            RefundCreateRequest(
                orderId = 10L, reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
                items = listOf(RefundItemRequest(orderItemId = ORDER_ITEM_ID, quantity = 1))
            )
        )

        val captor = argumentCaptor<RefundCompletedEvent>()
        verify(refundEventPublisher).publishRefundCompletedEvent(captor.capture())
        assertThat(captor.firstValue.items.map { it.orderItemId }).containsExactly(ORDER_ITEM_ID)
    }

    @Test
    fun 발행하는_메시지의_항목에_주문_항목_id가_들어간다() {
        val publisher = KafkaRefundEventPublisher(transactionalEventPublisher)

        publisher.publishRefundCompletedEvent(
            RefundCompletedEvent(
                eventId = "evt-1", refundId = 1L, orderId = 10L, paymentId = 20L, buyerId = 100L,
                refundAmount = BigDecimal("10000"), pointRefundAmount = BigDecimal.ZERO, isFullRefund = false,
                items = listOf(
                    RefundItemEvent(productId = 200L, quantity = 1, refundPrice = BigDecimal("10000"), orderItemId = 31L),
                    RefundItemEvent(productId = 200L, quantity = 1, refundPrice = BigDecimal("10000"), orderItemId = 32L)
                )
            )
        )

        val eventData = argumentCaptor<Any>()
        verify(transactionalEventPublisher).publishEvent(
            any(), any(), any(), eventData.capture(), any(), anyOrNull()
        )
        @Suppress("UNCHECKED_CAST")
        val items = (eventData.firstValue as Map<String, Any?>)["items"] as List<Map<String, Any?>>
        assertThat(items.map { it["orderItemId"] }).containsExactly(31L, 32L)
    }

    companion object {
        private const val ORDER_ITEM_ID = 7L
    }
}
