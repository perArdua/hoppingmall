package com.hoppingmall.order.shipping.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.OrderShippingAddress
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.order.exception.OrderShippingAddressRequiredException
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.dto.request.ShippingCreateRequest
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

@DisplayName("배송 등록은 주문에 남은 배송지를 쓴다")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ShippingCreateFromOrderAddressTest {

    private val shippingRepository: ShippingRepository = mock()
    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val service = ShippingCommandServiceImpl(
        shippingRepository, orderRepository, orderItemRepository, mock<TransactionalEventPublisher>(), ObjectMapper()
    )

    @Test
    fun 배송지가_있는_주문은_요청의_받는_사람_값을_쓰지_않고_주문의_배송지로_배송을_만든다() {
        paidOrder(withAddress = true)

        val result = service.createShipping(
            SELLER_ID,
            request(recipientName = "다른 사람", recipientPhone = "010-9999-9999", recipientAddress = "다른 주소")
        )

        assertThat(result.recipientName).isEqualTo("홍길동")
        assertThat(result.recipientPhone).isEqualTo("010-1234-5678")
        assertThat(result.recipientAddress).isEqualTo("(06236) 서울시 강남구 테헤란로 1 101동 202호")
    }

    @Test
    fun 배송지가_있는_주문은_받는_사람_값_없이_배송을_등록한다() {
        paidOrder(withAddress = true)

        val result = service.createShipping(SELLER_ID, request())

        assertThat(result.recipientName).isEqualTo("홍길동")
    }

    @Test
    fun 배송지가_없는_옛_주문은_요청의_받는_사람_값으로_배송을_만든다() {
        paidOrder(withAddress = false)

        val result = service.createShipping(
            SELLER_ID,
            request(recipientName = "김철수", recipientPhone = "010-2222-3333", recipientAddress = "부산시 해운대구")
        )

        assertThat(result.recipientName).isEqualTo("김철수")
        assertThat(result.recipientPhone).isEqualTo("010-2222-3333")
        assertThat(result.recipientAddress).isEqualTo("부산시 해운대구")
    }

    @Test
    fun 배송지가_없는_옛_주문에_받는_사람_값이_모자라면_거절하고_저장하지_않는다() {
        paidOrder(withAddress = false)

        assertThatThrownBy { service.createShipping(SELLER_ID, request(recipientName = "김철수", recipientPhone = " ")) }
            .isInstanceOf(OrderShippingAddressRequiredException::class.java)

        verify(shippingRepository, never()).save(any<Shipping>())
    }

    private fun paidOrder(withAddress: Boolean) {
        val address = OrderShippingAddress(
            recipientName = "홍길동", recipientPhone = "010-1234-5678", zipCode = "06236",
            address = "서울시 강남구 테헤란로 1", addressDetail = "101동 202호", deliveryMemo = "문 앞"
        )
        val order = Order.create(
            buyerId = BUYER_ID, totalAmount = BigDecimal("50000"),
            shippingAddress = if (withAddress) address else null
        )
        ReflectionTestUtils.setField(order, "id", ORDER_ID)
        order.updateStatus(OrderStatus.PAYING)
        order.updateStatus(OrderStatus.PAID)
        val item = OrderItem.create(
            orderId = ORDER_ID, sellerId = SELLER_ID, productId = 100L,
            productName = "상품", productPrice = BigDecimal("50000"), quantity = 1
        )
        whenever(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order))
        whenever(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(listOf(item))
        whenever(shippingRepository.findByOrderId(ORDER_ID)).thenReturn(null)
        whenever(shippingRepository.save(any<Shipping>())).thenAnswer {
            val shipping = it.arguments[0] as Shipping
            ReflectionTestUtils.setField(shipping, "id", 1L)
            shipping
        }
    }

    private fun request(recipientName: String? = null, recipientPhone: String? = null, recipientAddress: String? = null) =
        ShippingCreateRequest(
            orderId = ORDER_ID, carrierName = "CJ대한통운", trackingNumber = "1234567890",
            recipientName = recipientName, recipientPhone = recipientPhone, recipientAddress = recipientAddress
        )

    companion object {
        private const val ORDER_ID = 10L
        private const val BUYER_ID = 1L
        private const val SELLER_ID = 5L
    }
}
