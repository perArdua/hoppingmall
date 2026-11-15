package com.hoppingmall.order.refund.domain

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.refund.config.RefundPolicyProperties
import com.hoppingmall.order.refund.enum.RefundReason
import com.hoppingmall.order.shipping.domain.Shipping
import com.hoppingmall.order.shipping.enum.ShippingStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@DisplayName("환불 요청 기한 계산")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefundDeadlineTest {

    private val deadline = RefundDeadline(RefundPolicyProperties())

    @Test
    fun 단순_변심의_마지막_날은_받은_날짜에_7일을_더한_날이고_그_날_끝까지_받는다() {
        val receivedAt = LocalDateTime.of(2026, 11, 8, 23, 50)

        assertThat(deadline.lastDay(RefundReason.CHANGE_OF_MIND, receivedAt)).isEqualTo(LocalDate.of(2026, 11, 15))
        assertThat(deadline.isClosed(RefundReason.CHANGE_OF_MIND, receivedAt, LocalDateTime.of(2026, 11, 15, 23, 59))).isFalse()
        assertThat(deadline.isClosed(RefundReason.CHANGE_OF_MIND, receivedAt, LocalDateTime.of(2026, 11, 16, 0, 0))).isTrue()
    }

    @Test
    fun 하자류의_마지막_날은_받은_날짜에_3개월을_더한_날이다() {
        val receivedAt = LocalDateTime.of(2026, 8, 15, 10, 0)

        listOf(RefundReason.DEFECTIVE, RefundReason.WRONG_ITEM, RefundReason.NOT_AS_DESCRIBED).forEach { reason ->
            assertThat(deadline.lastDay(reason, receivedAt)).isEqualTo(LocalDate.of(2026, 11, 15))
            assertThat(deadline.isClosed(reason, receivedAt, LocalDateTime.of(2026, 11, 15, 23, 59))).isFalse()
            assertThat(deadline.isClosed(reason, receivedAt, LocalDateTime.of(2026, 11, 16, 0, 0))).isTrue()
        }
    }

    @Test
    fun 달_끝에_받으면_3개월_뒤_달의_마지막_날까지다() {
        val receivedAt = LocalDateTime.of(2026, 11, 30, 9, 0)

        assertThat(deadline.lastDay(RefundReason.DEFECTIVE, receivedAt)).isEqualTo(LocalDate.of(2027, 2, 28))
    }

    @Test
    fun 끝남_확인의_기준_시각은_가장_긴_기한과_보관_일수를_뺀_날의_시작이다() {
        assertThat(deadline.allWindowsClosedBefore(LocalDate.of(2026, 11, 15), 30))
            .isEqualTo(LocalDate.of(2026, 7, 16).atStartOfDay())

        val longChangeOfMind = RefundDeadline(RefundPolicyProperties(changeOfMindWindowDays = 120, sellerFaultWindowMonths = 1))
        assertThat(longChangeOfMind.allWindowsClosedBefore(LocalDate.of(2026, 11, 15), 0))
            .isEqualTo(LocalDate.of(2026, 7, 18).atStartOfDay())
    }

    @Test
    fun 받은_시각은_주문의_배송_완료_시각을_먼저_쓴다() {
        val deliveredAt = LocalDateTime.of(2026, 11, 1, 10, 0)
        val order = deliveredOrder(deliveredAt)

        assertThat(RefundDeadline.receivedAt(order, deliveredShipping(LocalDateTime.of(2026, 11, 3, 10, 0))))
            .isEqualTo(deliveredAt)
    }

    @Test
    fun 주문의_배송_완료_시각이_비면_배송_완료_행의_변경_시각을_쓴다() {
        val shippedAt = LocalDateTime.of(2026, 11, 3, 10, 0)

        assertThat(RefundDeadline.receivedAt(deliveredOrder(null), deliveredShipping(shippedAt))).isEqualTo(shippedAt)
    }

    @Test
    fun 배송_행이_배송_완료가_아니면_배송_완료_주문의_변경_시각을_쓴다() {
        val order = deliveredOrder(null)
        val orderUpdatedAt = LocalDateTime.of(2026, 11, 4, 10, 0)
        ReflectionTestUtils.setField(order, "updatedAt", orderUpdatedAt)
        val inTransit = shipping()
        inTransit.updateStatus(ShippingStatus.IN_TRANSIT)

        assertThat(RefundDeadline.receivedAt(order, inTransit)).isEqualTo(orderUpdatedAt)
        assertThat(RefundDeadline.receivedAt(order, null)).isEqualTo(orderUpdatedAt)
    }

    @Test
    fun 아직_받지_않은_주문은_받은_시각이_없다() {
        val order = Order.create(buyerId = 1L, totalAmount = BigDecimal("10000"))
        listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED).forEach { order.updateStatus(it) }
        ReflectionTestUtils.setField(order, "updatedAt", LocalDateTime.of(2026, 11, 4, 10, 0))

        assertThat(RefundDeadline.receivedAt(order, null)).isNull()
    }

    private fun deliveredOrder(deliveredAt: LocalDateTime?): Order {
        val order = Order.create(buyerId = 1L, totalAmount = BigDecimal("10000"))
        listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED)
            .forEach { order.updateStatus(it) }
        ReflectionTestUtils.setField(order, "deliveredAt", deliveredAt)
        return order
    }

    private fun deliveredShipping(completedAt: LocalDateTime): Shipping {
        val shipping = shipping()
        shipping.updateStatus(ShippingStatus.IN_TRANSIT)
        shipping.updateStatus(ShippingStatus.DELIVERED)
        ReflectionTestUtils.setField(shipping, "updatedAt", completedAt)
        return shipping
    }

    private fun shipping(): Shipping = Shipping.create(
        orderId = 1L, buyerId = 1L, carrierName = "택배", trackingNumber = "T-1",
        recipientName = "구매자", recipientPhone = "010-0000-0000", recipientAddress = "주소"
    )
}
