package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderAccessDeniedException
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.service.ShippingCommandServiceImpl
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.util.Optional

@DisplayName("주문 상태 변경 입구의 주문 주인 권한")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderStatusUpdateOwnerTest {

    @Mock
    private lateinit var shippingRepository: ShippingRepository

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    private lateinit var service: ShippingCommandServiceImpl

    @BeforeEach
    fun setUp() {
        service = ShippingCommandServiceImpl(
            shippingRepository, orderRepository, orderItemRepository, transactionalEventPublisher, ObjectMapper()
        )
    }

    @Test
    fun 주문_주인이_자기_PAYING_주문을_PAID로_바꾸려_하면_거절하고_주문은_PAYING에_남는다() {
        val order = order(OrderStatus.PAYING)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))

        assertThatThrownBy { service.updateStatusByOrder(BUYER_ID, ORDER_ID, OrderStatus.PAID, false) }
            .isInstanceOf(OrderAccessDeniedException::class.java)

        assertThat(order.status).isEqualTo(OrderStatus.PAYING)
        verify(orderItemRepository, never()).findByOrderId(any())
    }

    @Test
    fun 주문_주인이_자기_PAID_주문을_SHIPPED로_바꾸려_하면_거절하고_주문은_PAID에_남는다() {
        val order = order(OrderStatus.PAID)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))

        assertThatThrownBy { service.updateStatusByOrder(BUYER_ID, ORDER_ID, OrderStatus.SHIPPED, false) }
            .isInstanceOf(OrderAccessDeniedException::class.java)

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
        verify(orderItemRepository, never()).findByOrderId(any())
    }

    @Test
    fun 모든_주문을_바꿀_수_있는_호출자는_주문_주인이_아니어도_PAID_주문을_SHIPPED로_바꾼다() {
        val order = order(OrderStatus.PAID)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(emptyList())
        val shipping = Shipping.create(
            orderId = ORDER_ID, buyerId = BUYER_ID, carrierName = "CJ대한통운",
            trackingNumber = "1234567890", recipientName = "홍길동",
            recipientPhone = "010-1234-5678", recipientAddress = "서울시 강남구"
        )
        ReflectionTestUtils.setField(shipping, "id", SHIPPING_ID)
        Mockito.lenient().`when`(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(shipping)
        Mockito.lenient().`when`(shippingRepository.findById(SHIPPING_ID)).thenReturn(Optional.of(shipping))

        val result = service.updateStatusByOrder(OTHER_USER_ID, ORDER_ID, OrderStatus.SHIPPED, true)

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
        private const val SHIPPING_ID = 3L
        private const val BUYER_ID = 10L
        private const val OTHER_USER_ID = 99L
    }
}
