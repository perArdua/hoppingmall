package com.hoppingmall.order.internal

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("주문 끝남 확인 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderTerminalCheckMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Test
    fun 취소되거나_전체_환불된_지_30일이_지난_주문의_예약만_끝난_것이다() {
        saveOrder(OrderStatus.CANCELLED, daysAgo = 31, "rsv-cancelled-old")
        saveOrder(OrderStatus.REFUNDED, daysAgo = 31, "rsv-refunded-old")
        saveOrder(OrderStatus.CANCELLED, daysAgo = 29, "rsv-cancelled-recent")
        saveOrder(OrderStatus.REFUNDED, daysAgo = 29, "rsv-refunded-recent")
        saveOrder(OrderStatus.DELIVERED, daysAgo = 365, "rsv-delivered")
        saveOrder(OrderStatus.PAID, daysAgo = 365, "rsv-paid")
        saveOrder(OrderStatus.SHIPPED, daysAgo = 365, "rsv-shipped")
        saveOrder(OrderStatus.CANCEL_FAILED, daysAgo = 365, "rsv-cancel-failed")
        saveOrder(OrderStatus.CANCEL_REQUESTED, daysAgo = 365, "rsv-cancel-requested")

        val terminal = terminalCheck(
            listOf(
                "rsv-cancelled-old", "rsv-refunded-old", "rsv-cancelled-recent", "rsv-refunded-recent",
                "rsv-delivered", "rsv-paid", "rsv-shipped", "rsv-cancel-failed", "rsv-cancel-requested", "rsv-unknown"
            )
        )

        assertThat(terminal).containsExactly("rsv-cancelled-old", "rsv-refunded-old")
    }

    @Test
    fun 한_주문의_여러_예약은_각각_끝난_것으로_답한다() {
        saveOrder(OrderStatus.CANCELLED, daysAgo = 40, "rsv-a", "rsv-b")

        assertThat(terminalCheck(listOf("rsv-b", "rsv-a"))).containsExactly("rsv-a", "rsv-b")
    }

    @Test
    fun 묻지_않은_예약은_끝났어도_돌려주지_않는다() {
        saveOrder(OrderStatus.CANCELLED, daysAgo = 40, "rsv-asked")
        saveOrder(OrderStatus.CANCELLED, daysAgo = 40, "rsv-not-asked")

        assertThat(terminalCheck(listOf("rsv-asked"))).containsExactly("rsv-asked")
    }

    @Test
    fun 예약이_없는_주문_항목은_답에_섞이지_않는다() {
        saveOrder(OrderStatus.CANCELLED, daysAgo = 40, null)

        assertThat(terminalCheck(listOf("rsv-unknown"))).isEmpty()
    }

    @Test
    fun 같은_예약을_가진_주문이_하나라도_끝나지_않았으면_끝난_것이_아니다() {
        saveOrder(OrderStatus.CANCELLED, daysAgo = 40, "rsv-shared")
        saveOrder(OrderStatus.PAID, daysAgo = 40, "rsv-shared")

        assertThat(terminalCheck(listOf("rsv-shared"))).isEmpty()
    }

    @Test
    fun 상태가_된_시각이_없는_주문의_예약은_끝난_것이_아니다() {
        val orderId = saveOrder(OrderStatus.CANCELLED, daysAgo = 40, "rsv-no-updated-at")
        jdbcTemplate.update("UPDATE orders SET updated_at = NULL WHERE id = ?", orderId)

        assertThat(terminalCheck(listOf("rsv-no-updated-at"))).isEmpty()
    }

    @Test
    fun 예약_id_500개를_한_번에_묻는다() {
        saveOrder(OrderStatus.CANCELLED, daysAgo = 40, "rsv-250")
        val asked = (1..500).map { "rsv-$it" }

        assertThat(terminalCheck(asked)).containsExactly("rsv-250")
    }

    private fun terminalCheck(reservationIds: List<String>): List<String> =
        InternalOrderController(orderRepository, orderItemRepository, mock(), mock(), mock())
            .terminalCheck(InternalOrderController.TerminalCheckRequest(reservationIds))
            .body!!.data!!.terminalReservationIds

    private fun saveOrder(status: OrderStatus, daysAgo: Long, vararg reservationIds: String?): Long {
        val order = Order.create(buyerId = 1L, totalAmount = BigDecimal("10000"))
        PATHS.getValue(status).forEach { order.updateStatus(it) }
        if (status == OrderStatus.REFUNDED) order.completeRefund()
        val orderId = orderRepository.save(order).id!!
        reservationIds.forEachIndexed { index, reservationId ->
            orderItemRepository.save(
                OrderItem.create(
                    orderId = orderId,
                    sellerId = 7L,
                    productId = 100L + index,
                    productName = "상품",
                    productPrice = BigDecimal("1000"),
                    quantity = 1,
                    reservationId = reservationId
                )
            )
        }
        jdbcTemplate.update(
            "UPDATE orders SET updated_at = ? WHERE id = ?",
            LocalDateTime.now().minusDays(daysAgo),
            orderId
        )
        return orderId
    }

    companion object {
        private val PATHS = mapOf(
            OrderStatus.CANCELLED to listOf(OrderStatus.CANCELLED),
            OrderStatus.REFUNDED to listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED),
            OrderStatus.DELIVERED to listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED),
            OrderStatus.PAID to listOf(OrderStatus.PAYING, OrderStatus.PAID),
            OrderStatus.SHIPPED to listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED),
            OrderStatus.CANCEL_REQUESTED to listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.CANCEL_REQUESTED),
            OrderStatus.CANCEL_FAILED to listOf(
                OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCEL_FAILED
            )
        )
    }
}
