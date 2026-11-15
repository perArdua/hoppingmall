package com.hoppingmall.order.order.controller

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.OrderShippingAddress
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.exception.OrderAccessDeniedException
import com.hoppingmall.order.order.service.OrderCommandService
import com.hoppingmall.order.order.service.OrderQueryServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.util.Optional

@DisplayName("주문 상세의 배송지는 구매자·판매자·관리자만 본다")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderDetailShippingAddressAccessTest {

    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val controller = OrderController(mock<OrderCommandService>(), OrderQueryServiceImpl(orderRepository, orderItemRepository))

    @BeforeEach
    fun setUp() {
        val order = Order.create(
            buyerId = BUYER_ID,
            totalAmount = BigDecimal("50000"),
            shippingAddress = OrderShippingAddress(
                recipientName = "홍길동", recipientPhone = "010-1234-5678", zipCode = "06236",
                address = "서울시 강남구 테헤란로 1", addressDetail = "101동", deliveryMemo = null
            )
        )
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        val item = OrderItem.create(
            orderId = ORDER_ID, sellerId = SELLER_ID, productId = 100L,
            productName = "상품", productPrice = BigDecimal("50000"), quantity = 1
        )
        ReflectionTestUtils.setField(item, "id", 1L)
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(listOf(item))
    }

    @Test
    fun 구매자는_주문_상세에서_배송지를_본다() {
        val result = controller.getOrder(UserPrincipal.of(BUYER_ID, "BUYER"), ORDER_ID).body!!.data!!

        assertThat(result.shippingAddress!!.recipientPhone).isEqualTo("010-1234-5678")
    }

    @Test
    fun 주문_항목의_판매자는_주문_상세에서_배송지를_본다() {
        val result = controller.getOrder(UserPrincipal.of(SELLER_ID, "SELLER"), ORDER_ID).body!!.data!!

        assertThat(result.shippingAddress!!.address).isEqualTo("서울시 강남구 테헤란로 1")
    }

    @Test
    fun 관리자는_주문_상세에서_배송지를_본다() {
        val result = controller.getOrder(UserPrincipal.of(ADMIN_ID, "ADMIN"), ORDER_ID).body!!.data!!

        assertThat(result.shippingAddress!!.zipCode).isEqualTo("06236")
    }

    @Test
    fun 다른_구매자와_주문_항목이_없는_판매자는_주문_상세를_볼_수_없다() {
        assertThatThrownBy { controller.getOrder(UserPrincipal.of(OTHER_BUYER_ID, "BUYER"), ORDER_ID) }
            .isInstanceOf(OrderAccessDeniedException::class.java)
        assertThatThrownBy { controller.getOrder(UserPrincipal.of(OTHER_SELLER_ID, "SELLER"), ORDER_ID) }
            .isInstanceOf(OrderAccessDeniedException::class.java)
    }

    companion object {
        private const val ORDER_ID = 1L
        private const val BUYER_ID = 10L
        private const val OTHER_BUYER_ID = 11L
        private const val SELLER_ID = 20L
        private const val OTHER_SELLER_ID = 21L
        private const val ADMIN_ID = 99L
    }
}
