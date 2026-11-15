package com.hoppingmall.order.shipping.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.dto.request.ShippingCreateRequest
import com.hoppingmall.order.shipping.dto.request.ShippingStatusUpdateRequest
import com.hoppingmall.order.shipping.enum.ShippingStatus
import com.hoppingmall.order.shipping.exception.ShippingAccessDeniedException
import com.hoppingmall.order.shipping.exception.ShippingSelfOrderException
import com.hoppingmall.order.shipping.service.ShippingCommandServiceImpl
import com.hoppingmall.order.shipping.service.ShippingQueryServiceImpl
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.util.Optional

@DisplayName("구매자는 자기 주문의 배송을 등록·변경할 수 없다(검수 2의 B1·B2·B3 장면)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ShippingSelfOrderAccessTest {

    private val shippingRepository: ShippingRepository = mock()
    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val transactionalEventPublisher: TransactionalEventPublisher = mock()

    private val shippingController = ShippingController(
        ShippingCommandServiceImpl(
            shippingRepository, orderRepository, orderItemRepository, transactionalEventPublisher, ObjectMapper()
        ),
        ShippingQueryServiceImpl(shippingRepository, orderItemRepository)
    )

    private lateinit var order: Order
    private lateinit var shipping: Shipping

    @BeforeEach
    fun setUp() {
        order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("50000"))
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        shipping = Shipping.create(
            orderId = ORDER_ID, buyerId = BUYER_ID, carrierName = "CJ대한통운",
            trackingNumber = "1234567890", recipientName = "홍길동",
            recipientPhone = "010-1234-5678", recipientAddress = "서울시 강남구"
        )
        ReflectionTestUtils.setField(shipping, "id", SHIPPING_ID)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        whenever(shippingRepository.findById(SHIPPING_ID)).thenReturn(Optional.of(shipping))
        whenever(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(null)
        whenever(shippingRepository.save(any<Shipping>())).thenReturn(shipping)
    }

    @Test
    fun 구매자가_자기_PAID_주문에_배송을_등록하면_거절하고_저장하지_않는다() {
        itemsSoldBy(SELLER_ID)

        assertThatThrownBy { shippingController.createShipping(principal(BUYER_ID, "BUYER"), createRequest()) }
            .isInstanceOf(ShippingAccessDeniedException::class.java)

        verify(shippingRepository, never()).save(any<Shipping>())
    }

    @Test
    fun 구매자가_자기_주문의_배송을_DELIVERED로_바꾸면_거절하고_배송과_주문이_그대로다() {
        itemsSoldBy(SELLER_ID)
        startDelivery()

        assertThatThrownBy { changeShippingStatus(BUYER_ID, "BUYER", ShippingStatus.DELIVERED) }
            .isInstanceOf(ShippingAccessDeniedException::class.java)

        assertThat(shipping.status).isEqualTo(ShippingStatus.IN_TRANSIT)
        assertThat(order.status).isEqualTo(OrderStatus.SHIPPED)
        assertThat(order.deliveredAt).isNull()
    }

    @Test
    fun 다른_사용자는_주문_id로도_배송_id로도_남의_받는_사람_정보를_읽지_못한다() {
        itemsSoldBy(SELLER_ID)
        whenever(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(shipping)

        assertThatThrownBy { shippingController.getShippingByOrderId(principal(STRANGER_ID, "BUYER"), ORDER_ID) }
            .isInstanceOf(ShippingAccessDeniedException::class.java)
        assertThatThrownBy { shippingController.getShipping(principal(STRANGER_ID, "SELLER"), SHIPPING_ID) }
            .isInstanceOf(ShippingAccessDeniedException::class.java)
    }

    @Test
    fun 자기_상품을_산_판매자가_자기_주문에_배송을_등록하면_거절하고_저장하지_않는다() {
        itemsSoldBy(BUYER_ID)

        assertThatThrownBy { shippingController.createShipping(principal(BUYER_ID, "SELLER"), createRequest()) }
            .isInstanceOf(ShippingSelfOrderException::class.java)

        verify(shippingRepository, never()).save(any<Shipping>())
    }

    @Test
    fun 자기_상품을_산_판매자가_자기_주문의_배송을_DELIVERED로_바꾸면_거절하고_배송과_주문이_그대로다() {
        itemsSoldBy(BUYER_ID)
        startDelivery()

        assertThatThrownBy { changeShippingStatus(BUYER_ID, "SELLER", ShippingStatus.DELIVERED) }
            .isInstanceOf(ShippingSelfOrderException::class.java)

        assertThat(shipping.status).isEqualTo(ShippingStatus.IN_TRANSIT)
        assertThat(order.status).isEqualTo(OrderStatus.SHIPPED)
        assertThat(order.deliveredAt).isNull()
    }

    @Test
    fun 관리자는_자기_주문이어도_배송_상태를_바꾼다() {
        order = Order.create(buyerId = ADMIN_ID, totalAmount = BigDecimal("50000"))
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        itemsSoldBy(SELLER_ID)

        changeShippingStatus(ADMIN_ID, "ADMIN", ShippingStatus.IN_TRANSIT)

        assertThat(order.status).isEqualTo(OrderStatus.SHIPPED)
    }

    private fun itemsSoldBy(sellerId: Long) {
        val item = OrderItem.create(
            orderId = ORDER_ID, sellerId = sellerId, productId = 100L,
            productName = "테스트 상품", productPrice = BigDecimal("50000"), quantity = 1
        )
        whenever(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(listOf(item))
        whenever(orderItemRepository.existsByOrderIdAndSellerId(ORDER_ID, sellerId)).thenReturn(true)
    }

    private fun startDelivery() {
        shipping.updateStatus(ShippingStatus.IN_TRANSIT)
        order.updateStatus(OrderStatus.SHIPPED)
    }

    private fun changeShippingStatus(userId: Long, role: String, target: ShippingStatus) =
        shippingController.updateShippingStatus(principal(userId, role), SHIPPING_ID, ShippingStatusUpdateRequest(status = target))

    private fun createRequest() = ShippingCreateRequest(
        orderId = ORDER_ID, carrierName = "CJ대한통운", trackingNumber = "1234567890",
        recipientName = "홍길동", recipientPhone = "010-1234-5678", recipientAddress = "서울시 강남구"
    )

    private fun principal(userId: Long, role: String) = UserPrincipal.of(userId, role)

    companion object {
        private const val ORDER_ID = 10L
        private const val SHIPPING_ID = 1L
        private const val BUYER_ID = 1L
        private const val SELLER_ID = 5L
        private const val STRANGER_ID = 7L
        private const val ADMIN_ID = 99L
    }
}
