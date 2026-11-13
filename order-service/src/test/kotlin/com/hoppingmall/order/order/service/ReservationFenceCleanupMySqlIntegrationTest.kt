package com.hoppingmall.order.order.service

import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.support.MySqlJpaTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.UUID

@DisplayName("reservation_fence 행 정리 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ReservationFenceCleanupMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderItemRepository: OrderItemRepository

    @Test
    fun 보관_기간이_지난_만료_결정과_끝난_주문의_연결_결정을_지운다() {
        val aborted = fence("ABORTED", hoursAgo = 25)
        val cancelled = fence("LINKED", hoursAgo = 25, orderStatus = "CANCELLED")
        val delivered = fence("LINKED", hoursAgo = 25, orderStatus = "DELIVERED")

        val deleted = cleaner(enabled = true).cleanUp()

        assertThat(deleted).isEqualTo(3)
        assertThat(remaining()).doesNotContain(aborted, cancelled, delivered)
    }

    @Test
    fun 끝나지_않은_주문의_연결_결정과_보관_기간_안의_행은_남긴다() {
        val paying = fence("LINKED", hoursAgo = 25, orderStatus = "PAYING")
        val paid = fence("LINKED", hoursAgo = 25, orderStatus = "PAID")
        val shipped = fence("LINKED", hoursAgo = 25, orderStatus = "SHIPPED")
        val cancelFailed = fence("LINKED", hoursAgo = 25, orderStatus = "CANCEL_FAILED")
        val recentAborted = fence("ABORTED", hoursAgo = 23)
        val recentCancelled = fence("LINKED", hoursAgo = 23, orderStatus = "CANCELLED")

        val deleted = cleaner(enabled = true).cleanUp()

        assertThat(deleted).isZero()
        assertThat(remaining()).containsExactlyInAnyOrder(paying, paid, shipped, cancelFailed, recentAborted, recentCancelled)
    }

    @Test
    fun 한_번에_지우는_행_수는_배치_크기까지다() {
        repeat(5) { fence("ABORTED", hoursAgo = 30) }

        val first = cleaner(enabled = true, batchSize = 2).cleanUp()

        assertThat(first).isEqualTo(2)
        assertThat(remaining()).hasSize(3)
    }

    @Test
    fun fence가_꺼져_있으면_아무것도_지우지_않는다() {
        val aborted = fence("ABORTED", hoursAgo = 25)
        val cancelled = fence("LINKED", hoursAgo = 25, orderStatus = "CANCELLED")

        val deleted = cleaner(enabled = false).cleanUp()

        assertThat(deleted).isZero()
        assertThat(remaining()).containsExactlyInAnyOrder(aborted, cancelled)
    }

    private fun cleaner(enabled: Boolean, batchSize: Int = 1000) =
        ReservationFenceCleanupScheduler(jdbcTemplate, enabled, RETENTION_HOURS, batchSize)

    private fun fence(decision: String, hoursAgo: Long, orderStatus: String? = null): String {
        val rid = UUID.randomUUID().toString()
        jdbcTemplate.update(
            "INSERT INTO reservation_fence (reservation_id, decision, created_at) VALUES (?, ?, NOW(6) - INTERVAL ? HOUR)",
            rid, decision, hoursAgo
        )
        if (orderStatus != null) {
            val orderId = orderRepository.saveAndFlush(Order.create(buyerId = 1L, totalAmount = BigDecimal("1000"))).id!!
            orderItemRepository.saveAndFlush(
                OrderItem.create(
                    orderId = orderId, sellerId = 5L, productId = 100L,
                    productName = "상품", productPrice = BigDecimal("1000"),
                    quantity = 1, reservationId = rid
                )
            )
            jdbcTemplate.update("UPDATE orders SET status = ? WHERE id = ?", orderStatus, orderId)
        }
        return rid
    }

    private fun remaining(): List<String> =
        jdbcTemplate.queryForList("SELECT reservation_id FROM reservation_fence", String::class.java)

    companion object {
        private const val RETENTION_HOURS = 24L
    }
}
