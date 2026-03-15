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
