package com.hoppingmall.order.internal

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.refund.config.RefundPolicyProperties
import com.hoppingmall.order.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("배송 완료 주문의 끝남 확인 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderTerminalCheckDeliveredMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Test
    fun 배송_완료_뒤_환불_기한이_지난_지_30일이_지난_주문의_예약은_끝난_것이다() {
        saveDeliveredOrder(deliveredDaysAgo = 130, "rsv-delivered-130")
        saveDeliveredOrder(deliveredDaysAgo = 110, "rsv-delivered-110")

        assertThat(terminalCheck(controller(), listOf("rsv-delivered-130", "rsv-delivered-110")))
            .containsExactly("rsv-delivered-130")
    }

    @Test
    fun 배송_완료_시각이_없는_배송_완료_주문의_예약은_끝난_것이_아니다() {
        val orderId = saveDeliveredOrder(deliveredDaysAgo = 365, "rsv-no-delivered-at")
        jdbcTemplate.update("UPDATE orders SET delivered_at = NULL WHERE id = ?", orderId)

        assertThat(terminalCheck(controller(), listOf("rsv-no-delivered-at"))).isEmpty()
    }

    @Test
    fun 환불_기한과_보관_일수는_프로퍼티를_따른다() {
        val controller = controller()
        ReflectionTestUtils.setField(
            controller, "refundPolicy", RefundPolicyProperties(changeOfMindWindowDays = 7, sellerFaultWindowMonths = 1)
        )
        ReflectionTestUtils.setField(controller, "terminalRetentionDays", 3L)
        saveDeliveredOrder(deliveredDaysAgo = 40, "rsv-delivered-40")
        saveDeliveredOrder(deliveredDaysAgo = 30, "rsv-delivered-30")

        assertThat(terminalCheck(controller, listOf("rsv-delivered-40", "rsv-delivered-30")))
            .containsExactly("rsv-delivered-40")
    }

    @Test
    fun 끝난_배송_완료_주문과_끝난_취소_주문의_예약을_함께_답한다() {
        saveDeliveredOrder(deliveredDaysAgo = 130, "rsv-delivered")
        val cancelled = Order.create(buyerId = 1L, totalAmount = BigDecimal("10000"))
        cancelled.updateStatus(OrderStatus.CANCELLED)
        val cancelledId = orderRepository.save(cancelled).id!!
        saveItem(cancelledId, "rsv-cancelled")
        jdbcTemplate.update(
            "UPDATE orders SET updated_at = ? WHERE id = ?", LocalDateTime.now().minusDays(31), cancelledId
        )

        assertThat(terminalCheck(controller(), listOf("rsv-delivered", "rsv-cancelled")))
            .containsExactly("rsv-cancelled", "rsv-delivered")
    }

    private fun controller() = InternalOrderController(orderRepository, orderItemRepository, mock(), mock(), mock())

    private fun terminalCheck(controller: InternalOrderController, reservationIds: List<String>): List<String> =
        controller.terminalCheck(InternalOrderController.TerminalCheckRequest(reservationIds))
            .body!!.data!!.terminalReservationIds

    private fun saveDeliveredOrder(deliveredDaysAgo: Long, reservationId: String): Long {
        val order = Order.create(buyerId = 1L, totalAmount = BigDecimal("10000"))
        listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED)
            .forEach { order.updateStatus(it) }
        val orderId = orderRepository.save(order).id!!
        saveItem(orderId, reservationId)
        val deliveredAt = LocalDateTime.now().minusDays(deliveredDaysAgo)
        jdbcTemplate.update(
            "UPDATE orders SET delivered_at = ?, updated_at = ? WHERE id = ?", deliveredAt, deliveredAt, orderId
        )
        return orderId
    }

    private fun saveItem(orderId: Long, reservationId: String) {
        orderItemRepository.save(
            OrderItem.create(
                orderId = orderId,
                sellerId = 7L,
                productId = 100L,
                productName = "상품",
                productPrice = BigDecimal("1000"),
                quantity = 1,
                reservationId = reservationId
            )
        )
    }
}
