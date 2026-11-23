package com.hoppingmall.order.shipping.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.order.order.controller.OrderController
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.request.OrderStatusUpdateRequest
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderInvalidStatusException
import com.hoppingmall.order.order.service.OrderCommandService
import com.hoppingmall.order.order.service.OrderQueryService
import com.hoppingmall.order.shipping.controller.ShippingController
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.util.Optional

@DisplayName("배송 상태가 주문 상태를 끌고 간다")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ShippingDrivesOrderStatusTest {

    private val shippingRepository: ShippingRepository = mock()
    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val transactionalEventPublisher: TransactionalEventPublisher = mock()

    private val shippingService = ShippingCommandServiceImpl(
        shippingRepository, orderRepository, orderItemRepository, transactionalEventPublisher, ObjectMapper()
    )
    private val shippingController = ShippingController(shippingService, ShippingQueryServiceImpl(shippingRepository, orderItemRepository))
    private val orderController = OrderController(mock<OrderCommandService>(), mock<OrderQueryService>(), shippingService)

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
            trackingNumber = "TRK-1", recipientName = "홍길동",
            recipientPhone = "010-1234-5678", recipientAddress = "서울시 강남구"
        )
        ReflectionTestUtils.setField(shipping, "id", SHIPPING_ID)
        val item = OrderItem.create(
            orderId = ORDER_ID, sellerId = SELLER_ID, productId = 100L,
            productName = "상품", productPrice = BigDecimal("50000"), quantity = 1
        )
        ReflectionTestUtils.setField(item, "id", 7L)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        whenever(orderRepository.lockById(ORDER_ID)).thenReturn(ORDER_ID)
        whenever(shippingRepository.lockOrderIdById(SHIPPING_ID)).thenReturn(ORDER_ID)
        whenever(shippingRepository.findById(SHIPPING_ID)).thenReturn(Optional.of(shipping))
        whenever(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(shipping)
        whenever(shippingRepository.save(any<Shipping>())).thenAnswer { it.arguments[0] as Shipping }
        whenever(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(listOf(item))
        whenever(orderItemRepository.existsByOrderIdAndSellerId(ORDER_ID, SELLER_ID)).thenReturn(true)
    }

    @Test
    fun 주문_상태_API로_SHIPPED를_만들면_배송도_IN_TRANSIT이_되고_뒤의_배송_완료가_굳지_않는다() {
        val result = changeOrderStatus(OrderStatus.SHIPPED)

        assertThat(result.body!!.data!!.status).isEqualTo(OrderStatus.SHIPPED)
        assertThat(shipping.status).isEqualTo(ShippingStatus.IN_TRANSIT)

        changeShippingStatus(ShippingStatus.DELIVERED)

        assertThat(shipping.status).isEqualTo(ShippingStatus.DELIVERED)
        assertThat(order.status).isEqualTo(OrderStatus.DELIVERED)
        assertThat(order.deliveredAt).isNotNull()
    }

    @Test
    fun 주문_상태_API로_DELIVERED를_만들면_배송도_DELIVERED가_되고_배송_완료_알림이_나간다() {
        changeShippingStatus(ShippingStatus.IN_TRANSIT)

        changeOrderStatus(OrderStatus.DELIVERED)

        assertThat(shipping.status).isEqualTo(ShippingStatus.DELIVERED)
        assertThat(order.status).isEqualTo(OrderStatus.DELIVERED)
        verify(transactionalEventPublisher).publishEvent(
            any(), any(), eq("SHIPPING_DELIVEREDNotificationRequested"), any(), any(), any()
        )
    }

    @Test
    fun 배송이_없는_주문은_주문_상태_API로_SHIPPED가_되지_않는다() {
        whenever(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(null)

        assertThatThrownBy { changeOrderStatus(OrderStatus.SHIPPED) }
            .isInstanceOf(ShippingNotFoundException::class.java)

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
    }

    @Test
    fun 관리자도_주문_상태_입구로_PAID_주문을_REFUNDED로_보낼_수_없다() {
        assertThatThrownBy { shippingService.updateStatusByOrder(ADMIN_ID, ORDER_ID, OrderStatus.REFUNDED, true) }
            .isInstanceOf(OrderInvalidStatusException::class.java)

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
        assertThat(shipping.status).isEqualTo(ShippingStatus.PREPARING)
    }

    @Test
    fun 배송_FAILED는_주문을_PAID로_되돌린다() {
        changeShippingStatus(ShippingStatus.IN_TRANSIT)

        changeShippingStatus(ShippingStatus.FAILED)

        assertThat(shipping.status).isEqualTo(ShippingStatus.FAILED)
        assertThat(order.status).isEqualTo(OrderStatus.PAID)
    }

    @Test
    fun FAILED_배송이_있는_주문에_배송을_다시_등록하면_같은_배송이_새_운송장으로_PREPARING이_되고_시도_번호가_2가_된다() {
        changeShippingStatus(ShippingStatus.IN_TRANSIT)
        changeShippingStatus(ShippingStatus.FAILED)

        val result = shippingController.createShipping(seller(), createRequest("TRK-2"))

        assertThat(result.statusCode).isEqualTo(HttpStatus.CREATED)
        val body = result.body!!.data!!
        assertThat(body.id).isEqualTo(SHIPPING_ID)
        assertThat(body.status).isEqualTo(ShippingStatus.PREPARING)
        assertThat(body.trackingNumber).isEqualTo("TRK-2")
        assertThat(body.carrierName).isEqualTo("한진택배")
        assertThat(body.attempt).isEqualTo(2)
        assertThat(order.status).isEqualTo(OrderStatus.PAID)

        changeShippingStatus(ShippingStatus.IN_TRANSIT)
        assertThat(order.status).isEqualTo(OrderStatus.SHIPPED)
    }

    @Test
    fun CANCELLED_배송이_있는_결제_완료_주문에_배송을_다시_등록한다() {
        changeShippingStatus(ShippingStatus.CANCELLED)
        assertThat(order.status).isEqualTo(OrderStatus.PAID)

        val result = shippingController.createShipping(seller(), createRequest("TRK-2"))

        assertThat(result.body!!.data!!.status).isEqualTo(ShippingStatus.PREPARING)
        assertThat(result.body!!.data!!.attempt).isEqualTo(2)
    }

    @Test
    fun 다시_보낸_배송의_배송_시작_알림은_첫_시도와_다른_eventId로_나간다() {
        changeShippingStatus(ShippingStatus.IN_TRANSIT)
        changeShippingStatus(ShippingStatus.FAILED)
        shippingController.createShipping(seller(), createRequest("TRK-2"))
        changeShippingStatus(ShippingStatus.IN_TRANSIT)

        val eventData = argumentCaptor<Map<String, Any>>()
        verify(transactionalEventPublisher, times(2)).publishEvent(
            any(), any(), eq("SHIPPING_STARTEDNotificationRequested"), eventData.capture(), any(), any()
        )
        assertThat(eventData.allValues.map { it["eventId"] })
            .containsExactly("shipping-shipping_started-$SHIPPING_ID", "shipping-shipping_started-$SHIPPING_ID-2")
    }

    @Test
    fun 준비_중이거나_배송_중인_배송이_있으면_다시_등록하지_않는다() {
        assertThatThrownBy { shippingController.createShipping(seller(), createRequest("TRK-2")) }
            .isInstanceOf(ShippingAlreadyExistsException::class.java)

        changeShippingStatus(ShippingStatus.IN_TRANSIT)
        assertThatThrownBy { shippingController.createShipping(seller(), createRequest("TRK-3")) }
            .isInstanceOf(OrderInvalidStatusException::class.java)
        assertThat(shipping.trackingNumber).isEqualTo("TRK-1")
        assertThat(shipping.status).isEqualTo(ShippingStatus.IN_TRANSIT)
    }

    private fun changeOrderStatus(target: OrderStatus) =
        orderController.updateOrderStatus(seller(), ORDER_ID, OrderStatusUpdateRequest(status = target))

    private fun changeShippingStatus(target: ShippingStatus) =
        shippingController.updateShippingStatus(seller(), SHIPPING_ID, ShippingStatusUpdateRequest(status = target))

    private fun createRequest(trackingNumber: String) = ShippingCreateRequest(
        orderId = ORDER_ID, carrierName = "한진택배", trackingNumber = trackingNumber,
        recipientName = "홍길동", recipientPhone = "010-1234-5678", recipientAddress = "서울시 강남구"
    )

    private fun seller() = UserPrincipal.of(SELLER_ID, "SELLER")

    companion object {
        private const val ORDER_ID = 10L
        private const val SHIPPING_ID = 1L
        private const val BUYER_ID = 1L
        private const val SELLER_ID = 5L
        private const val ADMIN_ID = 99L
    }
}
