package com.hoppingmall.order.shipping.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderInvalidStatusException
import com.hoppingmall.order.order.exception.OrderNotFoundException
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.dto.request.ShippingCreateRequest
import com.hoppingmall.order.shipping.dto.request.ShippingStatusUpdateRequest
import com.hoppingmall.order.shipping.enum.ShippingStatus
import com.hoppingmall.order.shipping.exception.ShippingAlreadyExistsException
import com.hoppingmall.order.shipping.exception.ShippingNotFoundException
import com.hoppingmall.outbox.service.TransactionalEventPublisher
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.util.Optional

@DisplayName("ShippingCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ShippingCommandServiceImplTest {

    @Mock
    private lateinit var shippingRepository: ShippingRepository

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    @Mock
    private lateinit var transactionalEventPublisher: TransactionalEventPublisher

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @InjectMocks
    private lateinit var service: ShippingCommandServiceImpl

    private val shippingRequest = ShippingCreateRequest(
        orderId = 10L, carrierName = "CJ대한통운",
        trackingNumber = "1234567890", recipientName = "홍길동",
        recipientPhone = "010-1234-5678", recipientAddress = "서울시 강남구"
    )

    private fun createOrder(status: OrderStatus = OrderStatus.PAID): Order {
        val order = Order.create(buyerId = 1L, totalAmount = BigDecimal("50000"))
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        if (status == OrderStatus.SHIPPED) {
            order.updateStatus(OrderStatus.SHIPPED)
        }
        ReflectionTestUtils.setField(order, "id", 10L)
        return order
    }

    private fun createOrderItem(): OrderItem {
        val item = OrderItem.create(
            orderId = 10L, sellerId = 5L, productId = 100L,
            productName = "테스트 상품", productPrice = BigDecimal("50000"), quantity = 1
        )
        ReflectionTestUtils.setField(item, "id", 1L)
        return item
    }

    private fun createShipping(id: Long = 1L): Shipping {
        val shipping = Shipping.create(
            orderId = 10L, buyerId = 1L, carrierName = "CJ대한통운",
            trackingNumber = "1234567890", recipientName = "홍길동",
            recipientPhone = "010-1234-5678", recipientAddress = "서울시 강남구"
        )
        ReflectionTestUtils.setField(shipping, "id", id)
        return shipping
    }

    @Test
    fun 배송을_생성한다() {
        val order = createOrder()
        val orderItem = createOrderItem()
        val shipping = createShipping()

        whenever(orderRepository.findById(10L)).thenReturn(Optional.of(order))
        whenever(shippingRepository.findByOrderId(10L)).thenReturn(null)
        whenever(orderItemRepository.findByOrderId(10L)).thenReturn(listOf(orderItem))
        whenever(shippingRepository.save(any<Shipping>())).thenReturn(shipping)

        val result = service.createShipping(5L, shippingRequest)

        assertThat(result.orderId).isEqualTo(10L)
        assertThat(result.carrierName).isEqualTo("CJ대한통운")
    }

    @Test
    fun 존재하지_않는_주문에_배송생성_시_예외가_발생한다() {
        whenever(orderRepository.findById(10L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.createShipping(5L, shippingRequest) }
            .isInstanceOf(OrderNotFoundException::class.java)
    }

    @Test
    fun PAID가_아닌_주문에_배송생성_시_예외가_발생한다() {
        val order = Order.create(buyerId = 1L, totalAmount = BigDecimal("50000"))
        ReflectionTestUtils.setField(order, "id", 10L)

        whenever(orderRepository.findById(10L)).thenReturn(Optional.of(order))

        assertThatThrownBy { service.createShipping(5L, shippingRequest) }
            .isInstanceOf(OrderInvalidStatusException::class.java)
    }

    @Test
    fun 이미_배송이_존재하면_예외가_발생한다() {
        val order = createOrder()
        val existingShipping = createShipping()

        whenever(orderRepository.findById(10L)).thenReturn(Optional.of(order))
        whenever(shippingRepository.findByOrderId(10L)).thenReturn(existingShipping)

        assertThatThrownBy { service.createShipping(5L, shippingRequest) }
            .isInstanceOf(ShippingAlreadyExistsException::class.java)
    }

    @Test
    fun 배송중으로_상태변경_시_주문상태도_SHIPPED로_변경한다() {
        val request = ShippingStatusUpdateRequest(status = ShippingStatus.IN_TRANSIT)
        val shipping = createShipping()
        val order = createOrder()

        whenever(shippingRepository.findById(1L)).thenReturn(Optional.of(shipping))
        whenever(orderRepository.findById(10L)).thenReturn(Optional.of(order))
        whenever(objectMapper.writeValueAsString(any())).thenReturn("{}")

        val result = service.updateShippingStatus(5L, 1L, request)

        assertThat(result.status).isEqualTo(ShippingStatus.IN_TRANSIT)
        assertThat(order.status).isEqualTo(OrderStatus.SHIPPED)
        verify(transactionalEventPublisher).publishEvent(
            aggregateType = eq("Shipping"),
            aggregateId = any(),
            eventType = any(),
            eventData = any(),
            topic = any(),
            partitionKey = any()
        )
    }

    @Test
    fun 배송완료로_상태변경_시_주문상태도_DELIVERED로_변경한다() {
        val request = ShippingStatusUpdateRequest(status = ShippingStatus.DELIVERED)
        val shipping = createShipping()
        shipping.updateStatus(ShippingStatus.IN_TRANSIT)
        val order = createOrder(OrderStatus.SHIPPED)

        whenever(shippingRepository.findById(1L)).thenReturn(Optional.of(shipping))
        whenever(orderRepository.findById(10L)).thenReturn(Optional.of(order))
        whenever(objectMapper.writeValueAsString(any())).thenReturn("{}")

        val result = service.updateShippingStatus(5L, 1L, request)

        assertThat(result.status).isEqualTo(ShippingStatus.DELIVERED)
        assertThat(order.status).isEqualTo(OrderStatus.DELIVERED)
    }

    @Test
    fun 존재하지_않는_배송_상태변경_시_예외가_발생한다() {
        val request = ShippingStatusUpdateRequest(status = ShippingStatus.IN_TRANSIT)

        whenever(shippingRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.updateShippingStatus(5L, 999L, request) }
            .isInstanceOf(ShippingNotFoundException::class.java)
    }

    @Test
    fun 배송_상태변경_시_주문이_존재하지_않으면_예외가_발생한다() {
        val request = ShippingStatusUpdateRequest(status = ShippingStatus.IN_TRANSIT)
        val shipping = createShipping()

        whenever(shippingRepository.findById(1L)).thenReturn(Optional.of(shipping))
        whenever(orderRepository.findById(10L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.updateShippingStatus(5L, 1L, request) }
            .isInstanceOf(OrderNotFoundException::class.java)
    }
}
