package com.hoppingmall.order.internal

import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.refund.domain.Refund
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.refund.enum.RefundStatus
import com.hoppingmall.order.shipping.domain.repository.ShippingRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("InternalOrderController")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class InternalOrderControllerTest {

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    @Mock
    private lateinit var shippingRepository: ShippingRepository

    @Mock
    private lateinit var refundRepository: RefundRepository

    @InjectMocks
    private lateinit var controller: InternalOrderController

    @Test
    fun 주문의_주문상품_목록을_조회한다() {
        val orderItem = OrderItem.create(
            orderId = 10L, sellerId = 1L, productId = 100L,
            productName = "테스트 상품", productPrice = BigDecimal("10000"), quantity = 2
        )
        ReflectionTestUtils.setField(orderItem, "id", 1L)

        whenever(orderItemRepository.findByOrderId(10L)).thenReturn(listOf(orderItem))

        val response = controller.getOrderItems(10L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).hasSize(1)
        assertThat(response.body!![0].productName).isEqualTo("테스트 상품")
        assertThat(response.body!![0].totalPrice).isEqualByComparingTo(BigDecimal("20000"))
    }

    @Test
    fun 주문상품_단건을_조회한다() {
        val orderItem = OrderItem.create(
            orderId = 10L, sellerId = 1L, productId = 100L,
            productName = "테스트 상품", productPrice = BigDecimal("10000"), quantity = 1
        )
        ReflectionTestUtils.setField(orderItem, "id", 1L)

        whenever(orderItemRepository.findById(1L)).thenReturn(java.util.Optional.of(orderItem))

        val response = controller.getOrderItem(1L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.id).isEqualTo(1L)
    }

    @Test
    fun 존재하지_않는_주문상품_단건_조회_시_NOT_FOUND를_반환한다() {
        whenever(orderItemRepository.findById(999L)).thenReturn(java.util.Optional.empty())

        val response = controller.getOrderItem(999L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun 내부_API로_주문을_취소한다() {
        val order = com.hoppingmall.order.order.domain.Order.create(
            buyerId = 10L, totalAmount = BigDecimal("50000")
        )
        ReflectionTestUtils.setField(order, "id", 10L)

        whenever(orderRepository.findById(10L)).thenReturn(java.util.Optional.of(order))
        whenever(orderRepository.save(order)).thenReturn(order)

        val response = controller.cancelOrder(10L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 존재하지_않는_주문_취소_시_NOT_FOUND를_반환한다() {
        whenever(orderRepository.findById(999L)).thenReturn(java.util.Optional.empty())

        val response = controller.cancelOrder(999L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun 이미_취소된_주문_취소_시_OK를_반환한다() {
        val order = com.hoppingmall.order.order.domain.Order.create(
            buyerId = 10L, totalAmount = BigDecimal("50000")
        )
        order.updateStatus(com.hoppingmall.order.order.enum.OrderStatus.CANCELLED)
        ReflectionTestUtils.setField(order, "id", 10L)

        whenever(orderRepository.findById(10L)).thenReturn(java.util.Optional.of(order))

        val response = controller.cancelOrder(10L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun 배송완료_여부를_조회한다_배송완료() {
        val order = com.hoppingmall.order.order.domain.Order.create(
            buyerId = 10L, totalAmount = BigDecimal("50000")
        )
        ReflectionTestUtils.setField(order, "id", 10L)
        val shipping = com.hoppingmall.order.shipping.domain.Shipping.create(
            orderId = 10L, buyerId = 10L, carrierName = "CJ",
            trackingNumber = "1234", recipientName = "홍길동",
            recipientPhone = "010-0000-0000", recipientAddress = "서울"
        )
        shipping.updateStatus(com.hoppingmall.order.shipping.enum.ShippingStatus.IN_TRANSIT)
        shipping.updateStatus(com.hoppingmall.order.shipping.enum.ShippingStatus.DELIVERED)

        whenever(orderRepository.findById(10L)).thenReturn(java.util.Optional.of(order))
        whenever(shippingRepository.findByOrderId(10L)).thenReturn(shipping)

        val response = controller.isDelivered(10L, 10L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isTrue()
    }

    @Test
    fun 배송완료_여부를_조회한다_다른_구매자() {
        val order = com.hoppingmall.order.order.domain.Order.create(
            buyerId = 10L, totalAmount = BigDecimal("50000")
        )
        ReflectionTestUtils.setField(order, "id", 10L)

        whenever(orderRepository.findById(10L)).thenReturn(java.util.Optional.of(order))

        val response = controller.isDelivered(10L, 99L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isFalse()
    }

    @Test
    fun 배송완료_여부를_조회한다_주문없음() {
        whenever(orderRepository.findById(999L)).thenReturn(java.util.Optional.empty())

        val response = controller.isDelivered(999L, 10L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isFalse()
    }

    @Test
    fun 배송완료_여부를_조회한다_배송정보_없음() {
        val order = com.hoppingmall.order.order.domain.Order.create(
            buyerId = 10L, totalAmount = BigDecimal("50000")
        )
        ReflectionTestUtils.setField(order, "id", 10L)

        whenever(orderRepository.findById(10L)).thenReturn(java.util.Optional.of(order))
        whenever(shippingRepository.findByOrderId(10L)).thenReturn(null)

        val response = controller.isDelivered(10L, 10L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isFalse()
    }

    @Test
    fun 배송완료_주문상품을_판매자와_기간으로_조회한다() {
        val sellerId = 1L
        val startDate = LocalDateTime.of(2026, 1, 1, 0, 0)
        val endDate = LocalDateTime.of(2026, 1, 31, 23, 59, 59)

        val orderItem = OrderItem.create(
            orderId = 10L,
            sellerId = sellerId,
            productId = 100L,
            productName = "테스트 상품",
            productPrice = BigDecimal("10000"),
            quantity = 2
        )
        ReflectionTestUtils.setField(orderItem, "id", 1L)

        whenever(orderItemRepository.findDeliveredItemsBySellerAndPeriod(sellerId, startDate, endDate))
            .thenReturn(listOf(orderItem))

        val response = controller.getDeliveredOrderItems(sellerId, startDate, endDate)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).hasSize(1)
        assertThat(response.body!![0].id).isEqualTo(1L)
        assertThat(response.body!![0].sellerId).isEqualTo(sellerId)
        assertThat(response.body!![0].productName).isEqualTo("테스트 상품")
        assertThat(response.body!![0].totalPrice).isEqualByComparingTo(BigDecimal("20000"))
    }

    @Test
    fun 배송완료_주문상품이_없으면_빈_목록을_반환한다() {
        val sellerId = 1L
        val startDate = LocalDateTime.of(2026, 1, 1, 0, 0)
        val endDate = LocalDateTime.of(2026, 1, 31, 23, 59, 59)

        whenever(orderItemRepository.findDeliveredItemsBySellerAndPeriod(sellerId, startDate, endDate))
            .thenReturn(emptyList())

        val response = controller.getDeliveredOrderItems(sellerId, startDate, endDate)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isEmpty()
    }

    @Test
    fun 완료된_환불을_판매자와_기간으로_조회한다() {
        val sellerId = 1L
        val startDate = LocalDateTime.of(2026, 1, 1, 0, 0)
        val endDate = LocalDateTime.of(2026, 1, 31, 23, 59, 59)

        val refund = Refund.create(
            orderId = 10L,
            paymentId = 20L,
            buyerId = 30L,
            sellerId = sellerId,
            reason = RefundReason.CHANGE_OF_MIND,
            reasonDetail = null,
            refundAmount = BigDecimal("5000"),
            isFullRefund = false
        )
        refund.approve(100L)
        refund.complete()
        ReflectionTestUtils.setField(refund, "id", 1L)

        whenever(refundRepository.findBySellerIdAndStatusAndCompletedAtBetween(
            sellerId, RefundStatus.COMPLETED, startDate, endDate
        )).thenReturn(listOf(refund))

        val response = controller.getCompletedRefunds(sellerId, startDate, endDate)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).hasSize(1)
        assertThat(response.body!![0].id).isEqualTo(1L)
        assertThat(response.body!![0].refundAmount).isEqualByComparingTo(BigDecimal("5000"))
    }

    @Test
    fun 완료된_환불이_없으면_빈_목록을_반환한다() {
        val sellerId = 1L
        val startDate = LocalDateTime.of(2026, 1, 1, 0, 0)
        val endDate = LocalDateTime.of(2026, 1, 31, 23, 59, 59)

        whenever(refundRepository.findBySellerIdAndStatusAndCompletedAtBetween(
            sellerId, RefundStatus.COMPLETED, startDate, endDate
        )).thenReturn(emptyList())

        val response = controller.getCompletedRefunds(sellerId, startDate, endDate)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isEmpty()
    }
}
