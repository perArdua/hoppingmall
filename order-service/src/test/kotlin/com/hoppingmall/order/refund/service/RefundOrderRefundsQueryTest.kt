package com.hoppingmall.order.refund.service

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.exception.OrderNotFoundException
import com.hoppingmall.order.refund.domain.Refund
import com.hoppingmall.order.refund.domain.RefundItem
import com.hoppingmall.order.refund.domain.repository.RefundItemRepository
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.refund.domain.repository.RefundStatusQuantityProjection
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.refund.exception.RefundAccessDeniedException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.util.Optional

@DisplayName("주문 하나의 환불 목록과 항목별 환불 수량")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class RefundOrderRefundsQueryTest {

    @Mock
    private lateinit var refundRepository: RefundRepository

    @Mock
    private lateinit var refundItemRepository: RefundItemRepository

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    private lateinit var service: RefundQueryServiceImpl

    @BeforeEach
    fun setUp() {
        service = RefundQueryServiceImpl(refundRepository, refundItemRepository, orderRepository, orderItemRepository)
    }

    @Test
    fun 항목마다_완료된_수량과_진행_중인_수량과_남은_환불_가능_수량을_준다() {
        givenOrder(buyerId = BUYER_ID)
        whenever(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(
            listOf(orderItem(ITEM_B, quantity = 1), orderItem(ITEM_A, quantity = 5))
        )
        whenever(refundItemRepository.findRefundQuantitiesByStatus(ORDER_ID)).thenReturn(
            listOf(
                quantity(ITEM_A, "COMPLETED", 2),
                quantity(ITEM_A, "REQUESTED", 1),
                quantity(ITEM_A, "APPROVED", 1),
                quantity(ITEM_B, "COMPLETED", 1)
            )
        )
        val refund = refund(id = 7L)
        whenever(refundRepository.findByOrderIdOrderByIdAsc(ORDER_ID)).thenReturn(listOf(refund))
        whenever(refundItemRepository.findByRefundIdIn(listOf(7L))).thenReturn(listOf(refundItem(refundId = 7L)))

        val response = service.getOrderRefunds(ORDER_ID, BUYER_ID)

        assertThat(response.orderId).isEqualTo(ORDER_ID)
        assertThat(response.items.map { it.orderItemId }).containsExactly(ITEM_A, ITEM_B)
        val itemA = response.items.first()
        assertThat(itemA.quantity).isEqualTo(5)
        assertThat(itemA.refundedQuantity).isEqualTo(2)
        assertThat(itemA.inProgressQuantity).isEqualTo(2)
        assertThat(itemA.refundableQuantity).isEqualTo(1)
        val itemB = response.items.last()
        assertThat(itemB.refundedQuantity).isEqualTo(1)
        assertThat(itemB.inProgressQuantity).isEqualTo(0)
        assertThat(itemB.refundableQuantity).isEqualTo(0)
        assertThat(response.refunds.single().id).isEqualTo(7L)
        assertThat(response.refunds.single().items.single().orderItemId).isEqualTo(ITEM_A)
    }

    @Test
    fun 환불이_없는_주문은_주문_수량을_모두_환불_가능_수량으로_준다() {
        givenOrder(buyerId = BUYER_ID)
        whenever(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(listOf(orderItem(ITEM_A, quantity = 2)))
        whenever(refundItemRepository.findRefundQuantitiesByStatus(ORDER_ID)).thenReturn(emptyList())
        whenever(refundRepository.findByOrderIdOrderByIdAsc(ORDER_ID)).thenReturn(emptyList())

        val response = service.getOrderRefunds(ORDER_ID, BUYER_ID)

        assertThat(response.items.single().refundableQuantity).isEqualTo(2)
        assertThat(response.refunds).isEmpty()
        verify(refundItemRepository, never()).findByRefundIdIn(org.mockito.kotlin.any())
    }

    @Test
    fun 다른_구매자의_주문이면_RFD002로_거절하고_환불을_읽지_않는다() {
        givenOrder(buyerId = 99L)

        assertThatThrownBy { service.getOrderRefunds(ORDER_ID, BUYER_ID) }
            .isInstanceOf(RefundAccessDeniedException::class.java)

        verify(refundRepository, never()).findByOrderIdOrderByIdAsc(ORDER_ID)
    }

    @Test
    fun 없는_주문이면_주문을_찾을_수_없다고_답한다() {
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty())

        assertThatThrownBy { service.getOrderRefunds(ORDER_ID, BUYER_ID) }
            .isInstanceOf(OrderNotFoundException::class.java)
    }

    private fun givenOrder(buyerId: Long) {
        val order = Order.create(buyerId = buyerId, totalAmount = BigDecimal("60000"))
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
    }

    private fun orderItem(id: Long, quantity: Int): OrderItem {
        val item = OrderItem.create(
            orderId = ORDER_ID, sellerId = 5L, productId = 100L + id,
            productName = "상품$id", productPrice = BigDecimal("10000"), quantity = quantity
        )
        ReflectionTestUtils.setField(item, "id", id)
        return item
    }

    private fun quantity(orderItemId: Long, status: String, quantity: Long) =
        object : RefundStatusQuantityProjection {
            override val orderItemId: Long = orderItemId
            override val status: String = status
            override val quantity: Long = quantity
        }

    private fun refund(id: Long): Refund {
        val refund = Refund.create(
            orderId = ORDER_ID, paymentId = 20L, buyerId = BUYER_ID, sellerId = 5L,
            reason = RefundReason.CHANGE_OF_MIND, reasonDetail = null,
            refundAmount = BigDecimal("20000"), isFullRefund = false
        )
        ReflectionTestUtils.setField(refund, "id", id)
        return refund
    }

    private fun refundItem(refundId: Long): RefundItem {
        val item = RefundItem.create(
            refundId = refundId, orderItemId = ITEM_A, productId = 101L,
            productName = "상품1", productPrice = BigDecimal("10000"), quantity = 2
        )
        ReflectionTestUtils.setField(item, "id", 70L)
        return item
    }

    companion object {
        private const val BUYER_ID = 1L
        private const val ORDER_ID = 10L
        private const val ITEM_A = 1L
        private const val ITEM_B = 2L
    }
}
