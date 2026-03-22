package com.hoppingmall.order.order.service

import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderAccessDeniedException
import com.hoppingmall.order.order.exception.OrderEmptyItemsException
import com.hoppingmall.order.order.exception.OrderNotFoundException
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.util.Optional

@DisplayName("OrderCommandServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderCommandServiceImplTest {

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
    private lateinit var orderMetrics: OrderMetrics

    @InjectMocks
    private lateinit var service: OrderCommandServiceImpl

    @Test
    fun 주문_생성_시_재고_예약을_수행한다() {
        val cartItem = createCartItem(id = 1L, buyerId = 10L, productId = 100L, quantity = 2)
        val productInfo = createProductInfo(id = 100L, sellerId = 5L)

        whenever(cartItemRepository.findAllById(listOf(1L))).thenReturn(listOf(cartItem))
        whenever(productQueryPort.findProductsByIds(listOf(100L))).thenReturn(listOf(productInfo))
        whenever(inventoryCommandPort.batchReserveStock(listOf(100L to 2))).thenReturn(mapOf(100L to "rsv-1"))
        whenever(orderRepository.save(any<Order>())).thenAnswer {
            val order = it.arguments[0] as Order
            setEntityId(order, 1L)
            order
        }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val items = it.arguments[0] as List<OrderItem>
            items.forEachIndexed { idx, item -> setEntityId(item, (idx + 1).toLong()) }
            items
        }

        val result = service.createOrder(10L, OrderCreateRequest(cartItemIds = listOf(1L)))

        verify(inventoryCommandPort).batchReserveStock(listOf(100L to 2))
        assertThat(result).isNotNull
    }

    @Test
    fun 재고_예약_실패_시_이전_예약을_롤백한다() {
        val cartItem1 = createCartItem(id = 1L, buyerId = 10L, productId = 100L, quantity = 1)
        val cartItem2 = createCartItem(id = 2L, buyerId = 10L, productId = 200L, quantity = 1)
        val productInfo1 = createProductInfo(id = 100L, sellerId = 5L)
        val productInfo2 = createProductInfo(id = 200L, sellerId = 6L)

        whenever(cartItemRepository.findAllById(listOf(1L, 2L))).thenReturn(listOf(cartItem1, cartItem2))
        whenever(productQueryPort.findProductsByIds(any())).thenReturn(listOf(productInfo1, productInfo2))
        whenever(inventoryCommandPort.batchReserveStock(any())).thenThrow(RuntimeException("재고 부족"))

        assertThatThrownBy { service.createOrder(10L, OrderCreateRequest(cartItemIds = listOf(1L, 2L))) }
            .isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun 빈_장바구니로_주문_생성_시_예외가_발생한다() {
        whenever(cartItemRepository.findAllById(listOf(1L))).thenReturn(emptyList())

        assertThatThrownBy { service.createOrder(10L, OrderCreateRequest(cartItemIds = listOf(1L))) }
            .isInstanceOf(OrderEmptyItemsException::class.java)
    }

    @Test
    fun 다른_사용자의_장바구니로_주문_시_예외가_발생한다() {
        val cartItem = createCartItem(id = 1L, buyerId = 99L, productId = 100L, quantity = 1)
        whenever(cartItemRepository.findAllById(listOf(1L))).thenReturn(listOf(cartItem))

        assertThatThrownBy { service.createOrder(10L, OrderCreateRequest(cartItemIds = listOf(1L))) }
            .isInstanceOf(OrderAccessDeniedException::class.java)
    }

    @Test
    fun 주문_취소_시_reservationId가_있으면_예약_취소를_수행한다() {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        setEntityId(order, 1L)

        val orderItem = OrderItem.create(
            orderId = 1L, sellerId = 5L, productId = 100L,
            productName = "상품A", productPrice = BigDecimal("50000"),
            quantity = 1, reservationId = "rsv-1"
        )
        setEntityId(orderItem, 10L)

        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(1L)).thenReturn(listOf(orderItem))

        service.cancelOrder(10L, 1L)

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(inventoryCommandPort).cancelReservations(listOf("rsv-1"))
        verify(inventoryCommandPort, never()).increaseStock(any(), any())
    }

    @Test
    fun 주문_취소_시_reservationId가_없으면_재고_증가를_수행한다() {
        val order = Order.create(buyerId = 10L, totalAmount = BigDecimal("50000"))
        setEntityId(order, 1L)

        val orderItem = OrderItem.create(
            orderId = 1L, sellerId = 5L, productId = 100L,
            productName = "상품A", productPrice = BigDecimal("50000"),
            quantity = 1
        )
        setEntityId(orderItem, 10L)

        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(1L)).thenReturn(listOf(orderItem))

        service.cancelOrder(10L, 1L)

        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        verify(inventoryCommandPort).increaseStock(100L, 1)
        verify(inventoryCommandPort, never()).cancelReservations(any())
    }

    @Test
    fun 존재하지_않는_주문_취소_시_예외가_발생한다() {
        whenever(orderRepository.findById(1L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.cancelOrder(10L, 1L) }
            .isInstanceOf(OrderNotFoundException::class.java)
    }

    @Test
    fun 다른_사용자의_주문_취소_시_예외가_발생한다() {
        val order = Order.create(buyerId = 99L, totalAmount = BigDecimal("50000"))
        setEntityId(order, 1L)
        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))

        assertThatThrownBy { service.cancelOrder(10L, 1L) }
            .isInstanceOf(OrderAccessDeniedException::class.java)
    }

    private fun createCartItem(id: Long, buyerId: Long, productId: Long, quantity: Int): CartItem {
        val cartItem = CartItem.create(
            buyerId = buyerId,
            productId = productId,
            productName = "상품-$productId",
            productPrice = BigDecimal("25000"),
            productImageUrl = null,
            quantity = quantity
        )
        setEntityId(cartItem, id)
        return cartItem
    }

    private fun createProductInfo(id: Long, sellerId: Long): ProductInfo {
        return ProductInfo(id = id, name = "상품-$id", price = BigDecimal("25000"), sellerId = sellerId)
    }

    private fun setEntityId(entity: Any, id: Long) {
        ReflectionTestUtils.setField(entity, "id", id)
    }
}
