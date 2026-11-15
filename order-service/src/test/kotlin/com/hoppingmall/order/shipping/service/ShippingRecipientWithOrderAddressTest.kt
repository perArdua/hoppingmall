package com.hoppingmall.order.shipping.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.OrderShippingAddress
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import com.hoppingmall.order.shipping.dto.request.ShippingCreateRequest
import com.hoppingmall.order.shipping.exception.ShippingRecipientNotAllowedException
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

@DisplayName("배송지가 있는 주문의 배송 등록은 받는 사람 값을 거절한다")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ShippingRecipientWithOrderAddressTest {

    private val shippingRepository: ShippingRepository = mock()
    private val orderRepository: OrderRepository = mock()
    private val orderItemRepository: OrderItemRepository = mock()
    private val service = ShippingCommandServiceImpl(
        shippingRepository, orderRepository, orderItemRepository, mock<TransactionalEventPublisher>(), ObjectMapper()
    )

    @BeforeEach
    fun setUp() {
        val order = Order.create(
            buyerId = BUYER_ID, totalAmount = BigDecimal("50000"),
            shippingAddress = OrderShippingAddress(
                recipientName = "홍길동", recipientPhone = "010-1234-5678", zipCode = "06236",
                address = "서울시 강남구 테헤란로 1", addressDetail = "101동 202호", deliveryMemo = null
            )
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

    @Test
    fun 받는_사람_세_값을_모두_보내면_400으로_거절하고_저장하지_않는다() {
        assertRejected(request(recipientName = "다른 사람", recipientPhone = "010-9999-9999", recipientAddress = "다른 주소"))
    }

    @Test
    fun 받는_사람_전화번호_하나만_보내도_거절한다() {
        assertRejected(request(recipientPhone = "010-9999-9999"))
    }

    @Test
    fun 받는_사람_값이_비어_있으면_보내지_않은_것으로_보고_주문의_배송지로_만든다() {
        val result = service.createShipping(SELLER_ID, request(recipientName = " ", recipientAddress = ""))

        assertThat(result.recipientName).isEqualTo("홍길동")
        assertThat(result.recipientAddress).isEqualTo("(06236) 서울시 강남구 테헤란로 1 101동 202호")
    }

    private fun assertRejected(request: ShippingCreateRequest) {
        assertThatThrownBy { service.createShipping(SELLER_ID, request) }
            .isInstanceOf(ShippingRecipientNotAllowedException::class.java)
            .satisfies({ assertThat((it as ShippingRecipientNotAllowedException).errorCode.status).isEqualTo(HttpStatus.BAD_REQUEST) })

        verify(shippingRepository, never()).save(any<Shipping>())
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
