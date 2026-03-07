package com.hoppingmall.mall.order.service

import com.hoppingmall.mall.order.domain.Order
import com.hoppingmall.mall.order.domain.OrderItem
import com.hoppingmall.mall.order.domain.repository.OrderItemRepository
import com.hoppingmall.mall.order.domain.repository.OrderRepository
import com.hoppingmall.mall.order.enum.OrderStatus
import com.hoppingmall.mall.order.exception.OrderAccessDeniedException
import com.hoppingmall.mall.order.exception.OrderNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import java.util.*

@DisplayName("OrderQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class OrderQueryServiceImplTest {

    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val orderQueryService = OrderQueryServiceImpl(orderRepository, orderItemRepository)

    @Nested
    @DisplayName("getOrder")
    inner class GetOrder {
        @Test
        fun 주문을_조회한다() {
            // given
            val buyerId = 1L
            val orderId = 1L
            val order = Order.fixture(buyerId = buyerId)
            val orderItems = listOf(OrderItem.fixture(orderId = orderId))

            whenever(orderRepository.findById(orderId)).thenReturn(Optional.of(order))
            whenever(orderItemRepository.findByOrderId(orderId)).thenReturn(orderItems)

            // when
            val response = orderQueryService.getOrder(orderId, buyerId)

            // then
            assertEquals(orderId, response.id)
            assertEquals(buyerId, response.buyerId)
            assertEquals(OrderStatus.CREATED, response.status)
            assertEquals(1, response.items.size)
        }

        @Test
        fun 존재하지_않는_주문이면_예외가_발생한다() {
            // given
            val orderId = 999L
            val buyerId = 1L

            whenever(orderRepository.findById(orderId)).thenReturn(Optional.empty())

            // when & then
            assertThrows<OrderNotFoundException> {
                orderQueryService.getOrder(orderId, buyerId)
            }
        }

        @Test
        fun 다른_사용자의_주문이면_예외가_발생한다() {
            // given
            val orderId = 1L
            val buyerId = 1L
            val order = Order.fixture(buyerId = 999L)

            whenever(orderRepository.findById(orderId)).thenReturn(Optional.of(order))

            // when & then
            assertThrows<OrderAccessDeniedException> {
                orderQueryService.getOrder(orderId, buyerId)
            }
        }
    }

    @Nested
    @DisplayName("getMyOrders")
    inner class GetMyOrders {
        @Test
        fun 내_주문_목록을_조회한다() {
            // given
            val buyerId = 1L
            val pageable = PageRequest.of(0, 20)
            val order1 = Order.fixture(buyerId = buyerId).withId(1L)
            val order2 = Order.fixture(buyerId = buyerId).withId(2L)
            val slice = SliceImpl(listOf(order1, order2), pageable, false)

            whenever(orderRepository.findByBuyerId(buyerId, pageable)).thenReturn(slice)
            whenever(orderItemRepository.findByOrderIdIn(listOf(1L, 2L))).thenReturn(listOf(
                OrderItem.fixture(orderId = 1L),
                OrderItem.fixture(orderId = 2L)
            ))

            // when
            val response = orderQueryService.getMyOrders(buyerId, pageable)

            // then
            assertEquals(2, response.content.size)
            assertEquals(false, response.hasNext())
        }
    }
}
