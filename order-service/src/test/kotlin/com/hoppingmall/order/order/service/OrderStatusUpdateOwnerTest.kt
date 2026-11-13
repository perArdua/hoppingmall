package com.hoppingmall.order.order.service

import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.request.OrderStatusUpdateRequest
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderAccessDeniedException
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.Optional

@DisplayName("주문 상태 변경 서비스의 주문 주인 권한")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderStatusUpdateOwnerTest {

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    @Mock
    private lateinit var cartItemRepository: CartItemRepository

    @Mock
    private lateinit var productQueryPort: ProductQueryPort

    @Mock
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @Mock
    private lateinit var paymentCommandPort: PaymentCommandPort

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Mock
    private lateinit var orderMetrics: OrderMetrics

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    @Mock
    private lateinit var refundRepository: RefundRepository

    private lateinit var service: OrderCommandServiceImpl

    @BeforeEach
    fun setUp() {
        service = OrderCommandServiceImpl(
            orderRepository, orderItemRepository, cartItemRepository,
            productQueryPort, inventoryCommandPort, paymentCommandPort,
            transactionalEventPublisher, orderMetrics, transactionTemplate, refundRepository
        )
    }

    @Test
    fun 주문_주인이_자기_PAYING_주문을_PAID로_바꾸려_하면_거절하고_주문은_PAYING에_남는다() {
        val order = order(OrderStatus.PAYING)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))

        assertThatThrownBy {
            service.updateOrderStatus(ORDER_ID, OrderStatusUpdateRequest(status = OrderStatus.PAID), BUYER_ID, false)
        }.isInstanceOf(OrderAccessDeniedException::class.java)

        assertThat(order.status).isEqualTo(OrderStatus.PAYING)
        verify(orderItemRepository, never()).findByOrderId(any())
    }

    @Test
    fun 주문_주인이_자기_PAID_주문을_SHIPPED로_바꾸려_하면_거절하고_주문은_PAID에_남는다() {
        val order = order(OrderStatus.PAID)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))

        assertThatThrownBy {
            service.updateOrderStatus(ORDER_ID, OrderStatusUpdateRequest(status = OrderStatus.SHIPPED), BUYER_ID, false)
        }.isInstanceOf(OrderAccessDeniedException::class.java)

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
        verify(orderItemRepository, never()).findByOrderId(any())
    }

    @Test
    fun 모든_주문을_바꿀_수_있는_호출자는_주문_주인이_아니어도_PAID_주문을_SHIPPED로_바꾼다() {
        val order = order(OrderStatus.PAID)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(emptyList())

        val result = service.updateOrderStatus(ORDER_ID, OrderStatusUpdateRequest(status = OrderStatus.SHIPPED), OTHER_USER_ID, true)

        assertThat(result.status).isEqualTo(OrderStatus.SHIPPED)
        assertThat(order.status).isEqualTo(OrderStatus.SHIPPED)
    }

    private fun order(status: OrderStatus): Order {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("50000"))
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        order.updateStatus(OrderStatus.PAYING)
        if (status == OrderStatus.PAID) order.updateStatus(OrderStatus.PAID)
        return order
    }

    companion object {
        private const val ORDER_ID = 1L
        private const val BUYER_ID = 10L
        private const val OTHER_USER_ID = 99L
    }
}
