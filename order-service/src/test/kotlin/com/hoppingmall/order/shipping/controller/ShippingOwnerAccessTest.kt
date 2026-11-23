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
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.util.Optional

@DisplayName("배송 조회·등록·상태 변경의 주문 구매자·판매자·관리자 권한")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ShippingOwnerAccessTest {

    private val shippingRepository: ShippingRepository = mock()
    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val transactionalEventPublisher: TransactionalEventPublisher = mock()

    private val controller = ShippingController(
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
        val item = OrderItem.create(
            orderId = ORDER_ID, sellerId = SELLER_ID, productId = 100L,
            productName = "테스트 상품", productPrice = BigDecimal("50000"), quantity = 1
        )
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        whenever(shippingRepository.findById(SHIPPING_ID)).thenReturn(Optional.of(shipping))
        whenever(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(null)
        whenever(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(listOf(item))
        whenever(orderItemRepository.existsByOrderIdAndSellerId(ORDER_ID, SELLER_ID)).thenReturn(true)
    }

    @Test
    fun 다른_구매자가_주문_id로_배송을_조회하면_거절한다() {
        whenever(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(shipping)

        assertThatThrownBy { controller.getShippingByOrderId(principal(OTHER_BUYER_ID, "BUYER"), ORDER_ID) }
            .isInstanceOf(ShippingAccessDeniedException::class.java)
    }

    @Test
    fun 다른_구매자가_배송_id로_배송을_조회하면_거절한다() {
        assertThatThrownBy { controller.getShipping(principal(OTHER_BUYER_ID, "BUYER"), SHIPPING_ID) }
            .isInstanceOf(ShippingAccessDeniedException::class.java)
    }

    @Test
    fun 주문_항목이_없는_판매자가_배송을_조회하면_거절한다() {
        whenever(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(shipping)

        assertThatThrownBy { controller.getShippingByOrderId(principal(OTHER_SELLER_ID, "SELLER"), ORDER_ID) }
            .isInstanceOf(ShippingAccessDeniedException::class.java)
        assertThatThrownBy { controller.getShipping(principal(OTHER_SELLER_ID, "SELLER"), SHIPPING_ID) }
            .isInstanceOf(ShippingAccessDeniedException::class.java)
    }

    @Test
    fun 주문의_구매자는_배송을_조회한다() {
        whenever(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(shipping)

        val byOrder = controller.getShippingByOrderId(principal(BUYER_ID, "BUYER"), ORDER_ID)
        val byId = controller.getShipping(principal(BUYER_ID, "BUYER"), SHIPPING_ID)

        assertThat(byOrder.body!!.data!!.recipientPhone).isEqualTo("010-1234-5678")
        assertThat(byId.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 주문_항목의_판매자는_배송을_조회한다() {
        whenever(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(shipping)

        assertThat(controller.getShippingByOrderId(principal(SELLER_ID, "SELLER"), ORDER_ID).statusCode).isEqualTo(HttpStatus.OK)
        assertThat(controller.getShipping(principal(SELLER_ID, "SELLER"), SHIPPING_ID).statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 관리자는_주문_항목이_없어도_배송을_조회한다() {
        whenever(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(shipping)

        assertThat(controller.getShippingByOrderId(principal(ADMIN_ID, "ADMIN"), ORDER_ID).statusCode).isEqualTo(HttpStatus.OK)
        assertThat(controller.getShipping(principal(ADMIN_ID, "ADMIN"), SHIPPING_ID).statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 주문_항목이_없는_판매자가_배송을_등록하면_거절하고_저장하지_않는다() {
        assertThatThrownBy { controller.createShipping(principal(OTHER_SELLER_ID, "SELLER"), createRequest()) }
            .isInstanceOf(ShippingAccessDeniedException::class.java)

        verify(shippingRepository, never()).save(any<Shipping>())
    }

    @Test
    fun 주문_항목의_판매자는_배송을_등록한다() {
        whenever(shippingRepository.save(any<Shipping>())).thenReturn(shipping)

        val result = controller.createShipping(principal(SELLER_ID, "SELLER"), createRequest())

        assertThat(result.statusCode).isEqualTo(HttpStatus.CREATED)
    }

    @Test
    fun 관리자는_주문_항목이_없어도_배송을_등록한다() {
        whenever(shippingRepository.save(any<Shipping>())).thenReturn(shipping)

        val result = controller.createShipping(principal(ADMIN_ID, "ADMIN"), createRequest())

        assertThat(result.statusCode).isEqualTo(HttpStatus.CREATED)
    }

    @Test
    fun 주문_항목이_없는_판매자가_배송_상태를_바꾸면_거절하고_배송과_주문이_그대로다() {
        assertThatThrownBy { changeShippingStatus(OTHER_SELLER_ID, "SELLER", ShippingStatus.IN_TRANSIT) }
            .isInstanceOf(ShippingAccessDeniedException::class.java)

        assertThat(shipping.status).isEqualTo(ShippingStatus.PREPARING)
        assertThat(order.status).isEqualTo(OrderStatus.PAID)
    }

    @Test
    fun 구매자가_배송_상태를_바꾸면_거절하고_배송과_주문이_그대로다() {
        assertThatThrownBy { changeShippingStatus(BUYER_ID, "BUYER", ShippingStatus.IN_TRANSIT) }
            .isInstanceOf(ShippingAccessDeniedException::class.java)

        assertThat(shipping.status).isEqualTo(ShippingStatus.PREPARING)
        assertThat(order.status).isEqualTo(OrderStatus.PAID)
    }

    @Test
    fun 주문_항목의_판매자는_배송_상태를_바꾸고_주문이_SHIPPED가_된다() {
        changeShippingStatus(SELLER_ID, "SELLER", ShippingStatus.IN_TRANSIT)

        assertThat(shipping.status).isEqualTo(ShippingStatus.IN_TRANSIT)
        assertThat(order.status).isEqualTo(OrderStatus.SHIPPED)
    }

    @Test
    fun 관리자는_주문_항목이_없어도_배송_상태를_바꾼다() {
        changeShippingStatus(ADMIN_ID, "ADMIN", ShippingStatus.IN_TRANSIT)

        assertThat(order.status).isEqualTo(OrderStatus.SHIPPED)
    }

    private fun changeShippingStatus(userId: Long, role: String, target: ShippingStatus) =
        controller.updateShippingStatus(principal(userId, role), SHIPPING_ID, ShippingStatusUpdateRequest(status = target))

    private fun createRequest() = ShippingCreateRequest(
        orderId = ORDER_ID, carrierName = "CJ대한통운", trackingNumber = "1234567890",
        recipientName = "홍길동", recipientPhone = "010-1234-5678", recipientAddress = "서울시 강남구"
    )

    private fun principal(userId: Long, role: String) = UserPrincipal.of(userId, role)

    companion object {
        private const val ORDER_ID = 10L
        private const val SHIPPING_ID = 1L
        private const val BUYER_ID = 1L
        private const val OTHER_BUYER_ID = 2L
        private const val SELLER_ID = 5L
        private const val OTHER_SELLER_ID = 6L
        private const val ADMIN_ID = 99L
    }
}
