package com.hoppingmall.order.internal

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.enum.OrderStatus
import com.hoppingmall.order.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("주문 끝남 확인 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderTerminalCheckMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Test
    fun 취소되거나_전체_환불된_지_30일이_지난_주문만_끝난_주문이다() {
        val cancelledOld = saveOrder(OrderStatus.CANCELLED, daysAgo = 31)
        val refundedOld = saveOrder(OrderStatus.REFUNDED, daysAgo = 31)
        val cancelledRecent = saveOrder(OrderStatus.CANCELLED, daysAgo = 29)
        val refundedRecent = saveOrder(OrderStatus.REFUNDED, daysAgo = 29)
        val deliveredOld = saveOrder(OrderStatus.DELIVERED, daysAgo = 365)
        val paidOld = saveOrder(OrderStatus.PAID, daysAgo = 365)
        val shippedOld = saveOrder(OrderStatus.SHIPPED, daysAgo = 365)
        val cancelFailedOld = saveOrder(OrderStatus.CANCEL_FAILED, daysAgo = 365)
        val cancelRequestedOld = saveOrder(OrderStatus.CANCEL_REQUESTED, daysAgo = 365)
        val missing = cancelRequestedOld + 10_000

        val terminal = terminalCheck(
            listOf(
                cancelledOld, refundedOld, cancelledRecent, refundedRecent, deliveredOld,
                paidOld, shippedOld, cancelFailedOld, cancelRequestedOld, missing
            )
        )

        assertThat(terminal).containsExactly(cancelledOld, refundedOld)
    }

    @Test
    fun 묻지_않은_주문은_끝났어도_돌려주지_않는다() {
        val asked = saveOrder(OrderStatus.CANCELLED, daysAgo = 40)
        saveOrder(OrderStatus.CANCELLED, daysAgo = 40)

        assertThat(terminalCheck(listOf(asked))).containsExactly(asked)
    }

    @Test
    fun 상태가_된_시각이_없는_주문은_끝난_주문으로_보지_않는다() {
        val orderId = saveOrder(OrderStatus.CANCELLED, daysAgo = 40)
        jdbcTemplate.update("UPDATE orders SET updated_at = NULL WHERE id = ?", orderId)

        assertThat(terminalCheck(listOf(orderId))).isEmpty()
    }

    private fun terminalCheck(orderIds: List<Long>): List<Long> =
        InternalOrderTerminalCheckController(orderRepository, 30)
            .terminalCheck(InternalOrderTerminalCheckController.TerminalCheckRequest(orderIds))
            .body!!.data!!.terminalOrderIds

    private fun saveOrder(status: OrderStatus, daysAgo: Long): Long {
        val order = Order.create(buyerId = 1L, totalAmount = BigDecimal("10000"))
        PATHS.getValue(status).forEach { order.updateStatus(it) }
        if (status == OrderStatus.REFUNDED) order.completeRefund()
        val orderId = orderRepository.save(order).id!!
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
