package com.hoppingmall.order.internal

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
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

@DisplayName("주문 끝남 확인의 취소된 주문 예약 목록 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderTerminalCheckCancelledMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Test
    fun 끝난_예약_중_취소된_주문의_예약만_cancelled_reservation_ids로_준다() {
        saveOrder(OrderStatus.CANCELLED, daysAgo = 31, "rsv-cancelled")
        saveOrder(OrderStatus.REFUNDED, daysAgo = 31, "rsv-refunded")
        val deliveredId = saveOrder(OrderStatus.DELIVERED, daysAgo = 365, "rsv-delivered")
        jdbcTemplate.update("UPDATE orders SET delivered_at = ? WHERE id = ?", LocalDateTime.now().minusDays(365), deliveredId)

        val data = terminalCheck(listOf("rsv-delivered", "rsv-refunded", "rsv-cancelled"))

        assertThat(texts(data.path("terminal_reservation_ids"))).containsExactly("rsv-cancelled", "rsv-delivered", "rsv-refunded")
        assertThat(texts(data.path("cancelled_reservation_ids"))).containsExactly("rsv-cancelled")
    }

    @Test
    fun 아직_끝나지_않은_취소_주문의_예약은_cancelled_reservation_ids에도_없다() {
        saveOrder(OrderStatus.CANCELLED, daysAgo = 29, "rsv-cancelled-recent")
        saveOrder(OrderStatus.CANCELLED, daysAgo = 40, "rsv-cancelled-old")

        val data = terminalCheck(listOf("rsv-cancelled-recent", "rsv-cancelled-old"))

        assertThat(texts(data.path("terminal_reservation_ids"))).containsExactly("rsv-cancelled-old")
        assertThat(texts(data.path("cancelled_reservation_ids"))).containsExactly("rsv-cancelled-old")
    }

    @Test
    fun 같은_예약을_가진_끝난_주문_중_하나라도_취소면_취소로_준다() {
        saveOrder(OrderStatus.REFUNDED, daysAgo = 40, "rsv-shared")
        saveOrder(OrderStatus.CANCELLED, daysAgo = 40, "rsv-shared")

        val data = terminalCheck(listOf("rsv-shared"))

        assertThat(texts(data.path("terminal_reservation_ids"))).containsExactly("rsv-shared")
        assertThat(texts(data.path("cancelled_reservation_ids"))).containsExactly("rsv-shared")
    }

    @Test
    fun 끝난_예약이_없어도_cancelled_reservation_ids는_빈_배열로_준다() {
        saveOrder(OrderStatus.PAID, daysAgo = 40, "rsv-paid")

        val asked = terminalCheck(listOf("rsv-paid")).path("cancelled_reservation_ids")
        val empty = terminalCheck(emptyList()).path("cancelled_reservation_ids")

        assertThat(asked.isArray).isTrue()
        assertThat(asked).isEmpty()
        assertThat(empty.isArray).isTrue()
        assertThat(empty).isEmpty()
    }

    private fun terminalCheck(reservationIds: List<String>): JsonNode {
        val data = InternalOrderController(orderRepository, orderItemRepository, mock(), mock(), mock())
            .terminalCheck(InternalOrderController.TerminalCheckRequest(reservationIds))
            .body!!.data!!
        return snakeCase.valueToTree(data)
    }

    private fun texts(node: JsonNode): List<String> = node.map { it.asText() }

    private fun saveOrder(status: OrderStatus, daysAgo: Long, vararg reservationIds: String): Long {
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
        jdbcTemplate.update("UPDATE orders SET updated_at = ? WHERE id = ?", LocalDateTime.now().minusDays(daysAgo), orderId)
        return orderId
    }

    companion object {
        private val snakeCase = ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

        private val PATHS = mapOf(
            OrderStatus.CANCELLED to listOf(OrderStatus.CANCELLED),
            OrderStatus.REFUNDED to listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED),
            OrderStatus.DELIVERED to listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED),
            OrderStatus.PAID to listOf(OrderStatus.PAYING, OrderStatus.PAID)
        )
    }
}
