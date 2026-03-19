package com.hoppingmall.order.order.service

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.exception.OrderAccessDeniedException
import com.hoppingmall.order.order.exception.OrderNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.util.Optional

@DisplayName("OrderQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderQueryServiceImplTest {

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    @InjectMocks
    private lateinit var service: OrderQueryServiceImpl

    @Test
    fun 주문_단건_조회_성공() {
        val buyerId = 1L
        val orderId = 1L
        val order = createOrder(id = orderId, buyerId = buyerId)
        val orderItems = listOf(createOrderItem(id = 10L, orderId = orderId))

        whenever(orderRepository.findById(orderId)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(orderId)).thenReturn(orderItems)

        val response = service.getOrder(orderId, buyerId)

        assertThat(response.buyerId).isEqualTo(buyerId)
        assertThat(response.items).hasSize(1)
        verify(orderRepository).findById(orderId)
        verify(orderItemRepository).findByOrderId(orderId)
    }

    @Test
    fun 존재하지_않는_주문_조회_시_예외가_발생한다() {
        whenever(orderRepository.findById(any())).thenReturn(Optional.empty())

        assertThatThrownBy { service.getOrder(999L, 1L) }
            .isInstanceOf(OrderNotFoundException::class.java)
    }

    @Test
    fun 다른_구매자의_주문_조회_시_예외가_발생한다() {
        val order = createOrder(id = 1L, buyerId = 1L)

        whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))

        assertThatThrownBy { service.getOrder(1L, 99L) }
            .isInstanceOf(OrderAccessDeniedException::class.java)
    }

    @Test
    fun 내_주문_목록_조회_성공() {
        val buyerId = 1L
        val pageable = PageRequest.of(0, 10)
        val order = createOrder(id = 1L, buyerId = buyerId)
        val orderSlice = SliceImpl(listOf(order), pageable, false)
        val orderItems = listOf(createOrderItem(id = 10L, orderId = 1L))

        whenever(orderRepository.findByBuyerId(buyerId, pageable)).thenReturn(orderSlice)
        whenever(orderItemRepository.findByOrderIdIn(listOf(1L))).thenReturn(orderItems)

        val result = service.getMyOrders(buyerId, pageable)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].buyerId).isEqualTo(buyerId)
        verify(orderRepository).findByBuyerId(buyerId, pageable)
        verify(orderItemRepository).findByOrderIdIn(listOf(1L))
    }

    @Test
    fun 주문_목록이_비어있으면_빈_슬라이스를_반환한다() {
        val buyerId = 1L
        val pageable = PageRequest.of(0, 10)
        val emptySlice = SliceImpl(emptyList<Order>(), pageable, false)

        whenever(orderRepository.findByBuyerId(buyerId, pageable)).thenReturn(emptySlice)
        whenever(orderItemRepository.findByOrderIdIn(emptyList())).thenReturn(emptyList())

        val result = service.getMyOrders(buyerId, pageable)

        assertThat(result.content).isEmpty()
    }

    private fun createOrder(id: Long, buyerId: Long): Order {
        val order = Order.create(buyerId = buyerId, totalAmount = BigDecimal("50000"))
        setEntityId(order, id)
        return order
    }

    private fun createOrderItem(id: Long, orderId: Long): OrderItem {
        val item = OrderItem.create(
            orderId = orderId,
            sellerId = 5L,
            productId = 100L,
            productName = "테스트 상품",
            productPrice = BigDecimal("50000"),
            quantity = 1
        )
        setEntityId(item, id)
        return item
    }

    private fun setEntityId(entity: Any, id: Long) {
        ReflectionTestUtils.setField(entity, "id", id)
    }
}
