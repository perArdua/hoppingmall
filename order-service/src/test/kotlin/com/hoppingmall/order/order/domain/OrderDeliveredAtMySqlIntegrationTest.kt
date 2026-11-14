package com.hoppingmall.order.order.domain

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

@DisplayName("주문 배송 완료 시각 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrderDeliveredAtMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Test
    fun 배송_완료로_바뀐_주문은_배송_완료_시각을_저장한다() {
        val before = LocalDateTime.now().minusSeconds(1)
        val order = Order.create(buyerId = 1L, totalAmount = BigDecimal("10000"))
        listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED)
            .forEach { order.updateStatus(it) }

        val orderId = orderRepository.save(order).id!!

        val deliveredAt = jdbcTemplate.queryForObject(
            "SELECT delivered_at FROM orders WHERE id = ?", LocalDateTime::class.java, orderId
        )
        assertThat(deliveredAt).isAfter(before).isBefore(LocalDateTime.now().plusSeconds(1))
    }

    @Test
    fun 배송_완료_전_주문은_배송_완료_시각이_없다() {
        val order = Order.create(buyerId = 1L, totalAmount = BigDecimal("10000"))
        listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED).forEach { order.updateStatus(it) }

        val orderId = orderRepository.save(order).id!!

        assertThat(orderRepository.findById(orderId).get().deliveredAt).isNull()
    }

    @Test
    fun 배송_완료_시각이_비어_있는_기존_행은_null로_읽힌다() {
        val order = Order.create(buyerId = 1L, totalAmount = BigDecimal("10000"))
        listOf(OrderStatus.PAYING, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED)
            .forEach { order.updateStatus(it) }
        val orderId = orderRepository.save(order).id!!
        jdbcTemplate.update("UPDATE orders SET delivered_at = NULL WHERE id = ?", orderId)

        val reloaded = orderRepository.findById(orderId).get()

        assertThat(reloaded.status).isEqualTo(OrderStatus.DELIVERED)
        assertThat(reloaded.deliveredAt).isNull()
    }
}
