package com.hoppingmall.mall.order.service

import com.hoppingmall.mall.cartItem.domain.CartItem
import com.hoppingmall.mall.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.mall.inventory.service.InventoryCommandService
import com.hoppingmall.mall.order.domain.Order
import com.hoppingmall.mall.order.domain.OrderItem
import com.hoppingmall.mall.order.domain.repository.OrderItemRepository
import com.hoppingmall.mall.order.domain.repository.OrderRepository
import com.hoppingmall.mall.order.dto.request.OrderCreateRequest
import com.hoppingmall.mall.order.dto.request.OrderStatusUpdateRequest
import com.hoppingmall.mall.order.enum.OrderStatus
import com.hoppingmall.mall.order.exception.OrderAccessDeniedException
import com.hoppingmall.mall.order.exception.OrderEmptyItemsException
import com.hoppingmall.mall.order.exception.OrderInvalidStatusException
import com.hoppingmall.mall.order.exception.OrderNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.fixture.paidFixture
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.util.*

@DisplayName("OrderCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class OrderCommandServiceImplTest {

    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val cartItemRepository: CartItemRepository = mock()
    private val inventoryCommandService: InventoryCommandService = mock()
    private val orderCommandService = OrderCommandServiceImpl(
        orderRepository, orderItemRepository, cartItemRepository, inventoryCommandService
    )

    @Nested
    @DisplayName("createOrder")
    inner class CreateOrder {
        @Test
        fun 주문을_생성한다() {
            // given
            val buyerId = 1L
            val request = OrderCreateRequest(cartItemIds = listOf(1L, 2L))
            val cartItem1 = CartItem.fixture(buyerId = 1L, productId = 100L, productName = "상품1", productPrice = 15000L, quantity = 2).withId(1L)
            val cartItem2 = CartItem.fixture(buyerId = 1L, productId = 200L, productName = "상품2", productPrice = 20000L, quantity = 1).withId(2L)

            val orderCaptor = argumentCaptor<Order>()

            whenever(cartItemRepository.findAllById(request.cartItemIds)).thenReturn(listOf(cartItem1, cartItem2))
            whenever(orderRepository.save(orderCaptor.capture())).thenAnswer {
                orderCaptor.lastValue.withId(1L)
            }
            whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val items = invocation.getArgument<List<OrderItem>>(0)
                items.mapIndexed { index, item -> item.withId(index.toLong() + 1) }
            }

            // when
            val response = orderCommandService.createOrder(buyerId, request)

            // then
            assertEquals(1L, response.id)
            assertEquals(buyerId, response.buyerId)
            assertEquals(OrderStatus.CREATED, response.status)
            assertEquals(BigDecimal("50000"), response.totalAmount)
            assertEquals(2, response.items.size)
            verify(inventoryCommandService).decreaseStock(100L, 2)
            verify(inventoryCommandService).decreaseStock(200L, 1)
            verify(cartItemRepository).deleteAllById(request.cartItemIds)
        }

        @Test
        fun 장바구니가_비어있으면_예외가_발생한다() {
            // given
            val buyerId = 1L
            val request = OrderCreateRequest(cartItemIds = listOf(999L))

            whenever(cartItemRepository.findAllById(request.cartItemIds)).thenReturn(emptyList())

            // when & then
            assertThrows<OrderEmptyItemsException> {
                orderCommandService.createOrder(buyerId, request)
            }
        }

        @Test
        fun 다른_사용자의_장바구니_아이템이면_예외가_발생한다() {
            // given
            val buyerId = 1L
            val request = OrderCreateRequest(cartItemIds = listOf(1L))
            val otherBuyerCartItem = CartItem.fixture(buyerId = 999L).withId(1L)

            whenever(cartItemRepository.findAllById(request.cartItemIds)).thenReturn(listOf(otherBuyerCartItem))

            // when & then
            assertThrows<OrderAccessDeniedException> {
                orderCommandService.createOrder(buyerId, request)
            }
        }
    }

    @Nested
    @DisplayName("cancelOrder")
    inner class CancelOrder {
        @Test
        fun 주문을_취소한다() {
            // given
            val buyerId = 1L
            val orderId = 1L
            val order = Order.fixture(buyerId = buyerId)
            val orderItems = listOf(OrderItem.fixture(orderId = orderId))

            whenever(orderRepository.findById(orderId)).thenReturn(Optional.of(order))
            whenever(orderItemRepository.findByOrderId(orderId)).thenReturn(orderItems)

            // when
            val response = orderCommandService.cancelOrder(buyerId, orderId)

            // then
            assertEquals(OrderStatus.CANCELLED, response.status)
            verify(inventoryCommandService).increaseStock(100L, 2)
        }

        @Test
        fun 존재하지_않는_주문이면_예외가_발생한다() {
            // given
            val buyerId = 1L
            val orderId = 999L

            whenever(orderRepository.findById(orderId)).thenReturn(Optional.empty())

            // when & then
            assertThrows<OrderNotFoundException> {
                orderCommandService.cancelOrder(buyerId, orderId)
            }
        }

        @Test
        fun 다른_사용자의_주문이면_예외가_발생한다() {
            // given
            val buyerId = 1L
            val orderId = 1L
            val order = Order.fixture(buyerId = 999L)

            whenever(orderRepository.findById(orderId)).thenReturn(Optional.of(order))

            // when & then
            assertThrows<OrderAccessDeniedException> {
                orderCommandService.cancelOrder(buyerId, orderId)
            }
        }

        @Test
        fun 취소_불가능한_상태이면_예외가_발생한다() {
            // given
            val buyerId = 1L
            val orderId = 1L
            val order = Order.fixture(buyerId = buyerId, status = OrderStatus.DELIVERED)

            whenever(orderRepository.findById(orderId)).thenReturn(Optional.of(order))

            // when & then
            assertThrows<OrderInvalidStatusException> {
                orderCommandService.cancelOrder(buyerId, orderId)
            }
        }
    }

    @Nested
    @DisplayName("updateOrderStatus")
    inner class UpdateOrderStatus {
        @Test
        fun 주문_상태를_변경한다() {
            // given
            val orderId = 1L
            val request = OrderStatusUpdateRequest(status = OrderStatus.PAID)
            val order = Order.fixture()
            val orderItems = listOf(OrderItem.fixture(orderId = orderId))

            whenever(orderRepository.findById(orderId)).thenReturn(Optional.of(order))
            whenever(orderItemRepository.findByOrderId(orderId)).thenReturn(orderItems)

            // when
            val response = orderCommandService.updateOrderStatus(orderId, request)

            // then
            assertEquals(OrderStatus.PAID, response.status)
        }

        @Test
        fun 존재하지_않는_주문이면_예외가_발생한다() {
            // given
            val orderId = 999L
            val request = OrderStatusUpdateRequest(status = OrderStatus.PAID)

            whenever(orderRepository.findById(orderId)).thenReturn(Optional.empty())

            // when & then
            assertThrows<OrderNotFoundException> {
                orderCommandService.updateOrderStatus(orderId, request)
            }
        }
    }
}
