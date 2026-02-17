package com.hoppingmall.mall.shipping.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.mall.global.common.service.TransactionalEventPublisher
import com.hoppingmall.mall.order.domain.Order
import com.hoppingmall.mall.order.domain.OrderItem
import com.hoppingmall.mall.order.domain.repository.OrderItemRepository
import com.hoppingmall.mall.order.domain.repository.OrderRepository
import com.hoppingmall.mall.order.enum.OrderStatus
import com.hoppingmall.mall.order.exception.OrderInvalidStatusException
import com.hoppingmall.mall.order.exception.OrderNotFoundException
import com.hoppingmall.mall.shipping.domain.Shipping
import com.hoppingmall.mall.shipping.domain.repository.ShippingRepository
import com.hoppingmall.mall.shipping.dto.request.ShippingCreateRequest
import com.hoppingmall.mall.shipping.dto.request.ShippingStatusUpdateRequest
import com.hoppingmall.mall.shipping.enum.ShippingStatus
import com.hoppingmall.mall.shipping.exception.ShippingAlreadyExistsException
import com.hoppingmall.mall.shipping.exception.ShippingInvalidStatusException
import com.hoppingmall.mall.shipping.exception.ShippingNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.fixture.inTransitFixture
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
import java.util.*

@DisplayName("ShippingCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ShippingCommandServiceImplTest {

    private val shippingRepository: ShippingRepository = mock()
    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val transactionalEventPublisher: TransactionalEventPublisher = mock()
    private val objectMapper: ObjectMapper = ObjectMapper()
    private val shippingCommandService = ShippingCommandServiceImpl(
        shippingRepository, orderRepository, orderItemRepository, transactionalEventPublisher, objectMapper
    )

    private val sellerId = 10L
    private val createRequest = ShippingCreateRequest(
        orderId = 1L,
        carrierName = "CJ대한통운",
        trackingNumber = "1234567890",
        recipientName = "홍길동",
        recipientPhone = "010-1234-5678",
        recipientAddress = "서울시 강남구 테헤란로 123"
    )

    @Nested
    @DisplayName("createShipping")
    inner class CreateShipping {
        @Test
        fun 송장을_등록한다() {
            // given
            val order = Order.paidFixture()
            val orderItems = listOf(OrderItem.fixture())

            whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
            whenever(shippingRepository.findByOrderId(1L)).thenReturn(null)
            whenever(orderItemRepository.findByOrderId(1L)).thenReturn(orderItems)
            whenever(shippingRepository.save(any<Shipping>())).thenAnswer { invocation ->
                invocation.getArgument<Shipping>(0).withId(1L)
            }

            // when
            val response = shippingCommandService.createShipping(sellerId, createRequest)

            // then
            assertEquals(1L, response.id)
            assertEquals(1L, response.orderId)
            assertEquals(ShippingStatus.PREPARING, response.status)
            assertEquals("CJ대한통운", response.carrierName)
            assertEquals("1234567890", response.trackingNumber)
            verify(shippingRepository).save(any<Shipping>())
        }

        @Test
        fun 주문이_존재하지_않으면_예외가_발생한다() {
            // given
            whenever(orderRepository.findById(1L)).thenReturn(Optional.empty())

            // when & then
            assertThrows<OrderNotFoundException> {
                shippingCommandService.createShipping(sellerId, createRequest)
            }
        }

        @Test
        fun PAID_상태가_아니면_예외가_발생한다() {
            // given
            val order = Order.fixture(status = OrderStatus.CREATED)

            whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))

            // when & then
            assertThrows<OrderInvalidStatusException> {
                shippingCommandService.createShipping(sellerId, createRequest)
            }
        }

        @Test
        fun 이미_배송_정보가_존재하면_예외가_발생한다() {
            // given
            val order = Order.paidFixture()
            val existingShipping = Shipping.fixture()

            whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))
            whenever(shippingRepository.findByOrderId(1L)).thenReturn(existingShipping)

            // when & then
            assertThrows<ShippingAlreadyExistsException> {
                shippingCommandService.createShipping(sellerId, createRequest)
            }
        }
    }

    @Nested
    @DisplayName("updateShippingStatus")
    inner class UpdateShippingStatus {
        @Test
        fun IN_TRANSIT로_변경하면_주문이_SHIPPED가_되고_알림이_발행된다() {
            // given
            val shipping = Shipping.fixture()
            val order = Order.paidFixture()
            val request = ShippingStatusUpdateRequest(status = ShippingStatus.IN_TRANSIT)

            whenever(shippingRepository.findById(1L)).thenReturn(Optional.of(shipping))
            whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))

            // when
            val response = shippingCommandService.updateShippingStatus(sellerId, 1L, request)

            // then
            assertEquals(ShippingStatus.IN_TRANSIT, response.status)
            assertEquals(OrderStatus.SHIPPED, order.status)
            verify(transactionalEventPublisher).publishEvent(
                aggregateType = eq("Shipping"),
                aggregateId = eq("1"),
                eventType = eq("SHIPPING_STARTEDNotificationRequested"),
                eventData = any(),
                topic = eq("notification"),
                partitionKey = eq("1")
            )
        }

        @Test
        fun DELIVERED로_변경하면_주문이_DELIVERED가_되고_알림이_발행된다() {
            // given
            val shipping = Shipping.inTransitFixture()
            val order = Order.fixture(status = OrderStatus.SHIPPED).withId(1L)
            val request = ShippingStatusUpdateRequest(status = ShippingStatus.DELIVERED)

            whenever(shippingRepository.findById(1L)).thenReturn(Optional.of(shipping))
            whenever(orderRepository.findById(1L)).thenReturn(Optional.of(order))

            // when
            val response = shippingCommandService.updateShippingStatus(sellerId, 1L, request)

            // then
            assertEquals(ShippingStatus.DELIVERED, response.status)
            assertEquals(OrderStatus.DELIVERED, order.status)
            verify(transactionalEventPublisher).publishEvent(
                aggregateType = eq("Shipping"),
                aggregateId = eq("1"),
                eventType = eq("SHIPPING_DELIVEREDNotificationRequested"),
                eventData = any(),
                topic = eq("notification"),
                partitionKey = eq("1")
            )
        }

        @Test
        fun 배송_정보가_없으면_예외가_발생한다() {
            // given
            val request = ShippingStatusUpdateRequest(status = ShippingStatus.IN_TRANSIT)

            whenever(shippingRepository.findById(999L)).thenReturn(Optional.empty())

            // when & then
            assertThrows<ShippingNotFoundException> {
                shippingCommandService.updateShippingStatus(sellerId, 999L, request)
            }
        }

        @Test
        fun 잘못된_상태_전이이면_예외가_발생한다() {
            // given
            val shipping = Shipping.fixture()
            val request = ShippingStatusUpdateRequest(status = ShippingStatus.DELIVERED)

            whenever(shippingRepository.findById(1L)).thenReturn(Optional.of(shipping))

            // when & then
            assertThrows<ShippingInvalidStatusException> {
                shippingCommandService.updateShippingStatus(sellerId, 1L, request)
            }
        }
    }
}
