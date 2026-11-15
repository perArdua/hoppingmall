package com.hoppingmall.order.order.controller

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.request.OrderStatusUpdateRequest
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderAccessDeniedException
import com.hoppingmall.order.order.service.OrderCommandService
import com.hoppingmall.order.order.service.OrderQueryService
import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.exception.ShippingSelfOrderException
import com.hoppingmall.order.shipping.service.ShippingCommandServiceImpl
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.util.Optional

@DisplayName("주문 상태 변경 API의 판매자 소유 검사")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderStatusUpdateSellerOwnershipTest {

    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val shippingRepository: ShippingRepository = mock()
    private val transactionalEventPublisher: TransactionalEventPublisher = mock()

    private val controller = OrderController(
        mock<OrderCommandService>(),
        mock<OrderQueryService>(),
        ShippingCommandServiceImpl(
            shippingRepository, orderRepository, orderItemRepository, transactionalEventPublisher, ObjectMapper()
        )
    )

    @Test
    fun 주문_항목이_없는_판매자가_PAID_주문을_SHIPPED로_바꾸면_거절하고_주문은_PAID에_남는다() {
        val order = paidOrder()

        assertThatThrownBy { changeStatus(OTHER_SELLER_ID, "SELLER", OrderStatus.SHIPPED) }
            .isInstanceOf(OrderAccessDeniedException::class.java)

        assertThat(order.status).isEqualTo(OrderStatus.PAID)
    }

    @Test
    fun 주문_항목이_없는_판매자가_SHIPPED_주문을_DELIVERED로_바꾸면_거절하고_주문은_SHIPPED에_남는다() {
        val order = paidOrder()
        order.updateStatus(OrderStatus.SHIPPED)

        assertThatThrownBy { changeStatus(OTHER_SELLER_ID, "SELLER", OrderStatus.DELIVERED) }
            .isInstanceOf(OrderAccessDeniedException::class.java)

        assertThat(order.status).isEqualTo(OrderStatus.SHIPPED)
        assertThat(order.deliveredAt).isNull()
    }

    @Test
    fun 주문_항목의_판매자는_PAID_주문을_SHIPPED로_바꾼다() {
        val order = paidOrder()
        whenever(orderItemRepository.existsByOrderIdAndSellerId(ORDER_ID, SELLER_ID)).thenReturn(true)

        val result = changeStatus(SELLER_ID, "SELLER", OrderStatus.SHIPPED)

        assertThat(result.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(order.status).isEqualTo(OrderStatus.SHIPPED)
    }

    @Test
    fun 관리자는_주문_항목이_없어도_PAID_주문을_SHIPPED로_바꾼다() {
        val order = paidOrder()

        changeStatus(ADMIN_ID, "ADMIN", OrderStatus.SHIPPED)

        assertThat(order.status).isEqualTo(OrderStatus.SHIPPED)
    }

    @Test
    fun 자기_상품을_산_판매자가_자기_주문을_DELIVERED로_바꾸면_거절하고_주문이_그대로다() {
        val order = Order.create(buyerId = SELLER_ID, totalAmount = BigDecimal("50000"))
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        order.updateStatus(OrderStatus.SHIPPED)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.existsByOrderIdAndSellerId(ORDER_ID, SELLER_ID)).thenReturn(true)

        assertThatThrownBy { changeStatus(SELLER_ID, "SELLER", OrderStatus.DELIVERED) }
            .isInstanceOf(ShippingSelfOrderException::class.java)

        assertThat(order.status).isEqualTo(OrderStatus.SHIPPED)
        assertThat(order.deliveredAt).isNull()
    }

    private fun changeStatus(userId: Long, role: String, target: OrderStatus) =
        controller.updateOrderStatus(UserPrincipal.of(userId, role), ORDER_ID, OrderStatusUpdateRequest(status = target))

    private fun paidOrder(): Order {
        val order = Order.create(buyerId = BUYER_ID, totalAmount = BigDecimal("50000"))
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        val shipping = Shipping.create(
            orderId = ORDER_ID, buyerId = BUYER_ID, carrierName = "CJ대한통운",
            trackingNumber = "1234567890", recipientName = "홍길동",
            recipientPhone = "010-1234-5678", recipientAddress = "서울시 강남구"
        )
        ReflectionTestUtils.setField(shipping, "id", SHIPPING_ID)
        whenever(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(shipping)
        whenever(shippingRepository.findById(SHIPPING_ID)).thenReturn(Optional.of(shipping))
        return order
    }

    companion object {
        private const val ORDER_ID = 1L
        private const val SHIPPING_ID = 3L
        private const val BUYER_ID = 10L
        private const val SELLER_ID = 20L
        private const val OTHER_SELLER_ID = 30L
        private const val ADMIN_ID = 99L
    }
}
